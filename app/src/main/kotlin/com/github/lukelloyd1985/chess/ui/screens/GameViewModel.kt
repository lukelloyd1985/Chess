package com.github.lukelloyd1985.chess.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.lukelloyd1985.chess.ChessApp
import com.github.lukelloyd1985.chess.core.Bot
import com.github.lukelloyd1985.chess.core.ChessGame
import com.github.lukelloyd1985.chess.core.GameResult
import com.github.lukelloyd1985.chess.core.Move
import com.github.lukelloyd1985.chess.core.Outcome
import com.github.lukelloyd1985.chess.core.Piece
import com.github.lukelloyd1985.chess.core.PieceType
import com.github.lukelloyd1985.chess.core.Position
import com.github.lukelloyd1985.chess.core.TimeControl
import com.github.lukelloyd1985.chess.data.OnlineGame
import com.github.lukelloyd1985.chess.data.SavedGame
import com.github.lukelloyd1985.chess.engine.StockfishEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class GameMode { BOT, LOCAL, ONLINE }

data class GameConfig(
    val mode: GameMode,
    val botId: String = "improver",
    val playerWhite: Boolean = true,
    val timeControlIndex: Int = 0,
    val onlineCode: String? = null,
    /** FEN to start from (e.g. a position picked in game analysis); null = the standard start. */
    val startFen: String? = null,
)

data class GameUiState(
    val position: Position = Position.initial(),
    val lastMove: Move? = null,
    val sans: List<String> = emptyList(),
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val pendingPromotion: Pair<Int, Int>? = null,
    val result: GameResult? = null,
    val thinking: Boolean = false,
    val hint: Move? = null,
    val flipped: Boolean = false,
    val whiteName: String = "White",
    val blackName: String = "Black",
    val whiteMs: Long? = null,
    val blackMs: Long? = null,
    val canMove: Boolean = false,
    val message: String? = null,
    val savedGameId: String? = null,
    val waitingForOpponent: String? = null, // online share code while waiting
    val drawOfferFromOpponent: Boolean = false,
    val drawOfferSent: Boolean = false,
    val captured: Pair<List<Int>, List<Int>> = emptyList<Int>() to emptyList(), // pieces captured by white, by black
    val canUndo: Boolean = false,
    val canHint: Boolean = false,
    val isOnline: Boolean = false,
    val engineError: String? = null,
)

class GameViewModel(app: Application, private val config: GameConfig) : AndroidViewModel(app) {
    private val chessApp = app as ChessApp
    private val engine = StockfishEngine(app)
    private val game = ChessGame(
        config.startFen?.let { fen -> runCatching { Position.fromFen(fen) }.getOrNull() } ?: Position.initial(),
    )
    private val bot: Bot? = if (config.mode == GameMode.BOT) Bot.byId(config.botId) else null
    private val timeControl = TimeControl.PRESETS.getOrElse(config.timeControlIndex) { TimeControl.PRESETS[0] }
        .takeIf { config.mode != GameMode.ONLINE } ?: TimeControl.PRESETS[0]

    private val uid: String? get() = chessApp.authManager.user.value?.uid
    private var online: OnlineGame? = null
    private var whiteMs: Long? = timeControl.initialMs
    private var blackMs: Long? = timeControl.initialMs
    private var botJob: Job? = null
    private var hintJob: Job? = null
    private var thinking = false
    private var hint: Move? = null
    private var selected: Int? = null
    private var targets: Set<Int> = emptySet()
    private var pendingPromotion: Pair<Int, Int>? = null
    private var message: String? = null
    private var savedId: String? = null
    private var engineError: String? = null
    private var drawOfferSent = false
    private var flippedOverride: Boolean? = null

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    init {
        publish()
        when (config.mode) {
            GameMode.BOT -> {
                viewModelScope.launch { runCatching { engine.ensureStarted() }.onFailure { engineError = it.message; publish() } }
                maybeBotMove()
            }
            GameMode.ONLINE -> observeOnline()
            GameMode.LOCAL -> {}
        }
        if (timeControl.isTimed) startClock()
    }

    private val humanIsWhite: Boolean
        get() = when (config.mode) {
            GameMode.BOT -> config.playerWhite
            GameMode.ONLINE -> online?.colorOf(uid ?: "") ?: true
            GameMode.LOCAL -> game.position.whiteToMove
        }

    private fun myTurn(): Boolean {
        if (game.isOver()) return false
        return when (config.mode) {
            GameMode.LOCAL -> true
            GameMode.BOT -> !thinking && game.position.whiteToMove == config.playerWhite
            GameMode.ONLINE -> {
                val o = online
                o != null && o.status == "active" && o.colorOf(uid ?: "") == game.position.whiteToMove
            }
        }
    }

    // ---- user actions ----

    fun onSquareClick(sq: Int) {
        if (!myTurn() || pendingPromotion != null) return
        val pos = game.position
        val sel = selected
        if (sel != null && sq in targets) {
            val candidates = pos.legalMoves().filter { it.from == sel && it.to == sq }
            if (candidates.any { it.promotion != PieceType.NONE }) {
                pendingPromotion = sel to sq
                publish()
            } else {
                candidates.firstOrNull()?.let { playHuman(it) }
            }
            return
        }
        val piece = pos.pieceAt(sq)
        if (piece != Piece.EMPTY && Piece.isWhite(piece) == pos.whiteToMove) {
            if (online != null && config.mode == GameMode.ONLINE && Piece.isWhite(piece) != humanIsWhite) return
            selected = sq
            targets = pos.legalMovesFrom(sq).map { it.to }.toSet()
        } else {
            clearSelection()
        }
        publish()
    }

    fun choosePromotion(type: Int?) {
        val p = pendingPromotion ?: return
        pendingPromotion = null
        if (type == null) {
            clearSelection()
            publish()
            return
        }
        playHuman(Move(p.first, p.second, type))
    }

    fun flipBoard() {
        flippedOverride = !(flippedOverride ?: defaultFlipped())
        publish()
    }

    fun requestHint() {
        if (!myTurn() || config.mode == GameMode.ONLINE) return
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            try {
                val r = engine.search(game.uciPositionCommand(), "movetime 700")
                hint = r.best.move?.let { Move.fromUci(it) }
                publish()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                engineError = e.message
                publish()
            }
        }
    }

    fun undo() {
        if (config.mode == GameMode.ONLINE || game.ply == 0) return
        botJob?.cancel()
        thinking = false
        val plies = if (config.mode == GameMode.BOT && game.position.whiteToMove == config.playerWhite) 2 else 1
        repeat(plies) { game.undo() }
        // If the bot is to move again (e.g. undid back to the start as black), let it play.
        clearSelection()
        publish()
        maybeBotMove()
    }

    fun resign() {
        if (game.isOver()) return
        when (config.mode) {
            GameMode.ONLINE -> {
                val white = humanIsWhite
                game.resign(whiteResigns = white)
                val code = config.onlineCode ?: return
                viewModelScope.launch {
                    runCatching { chessApp.onlineRepository.finish(code, if (white) "0-1" else "1-0", "resignation") }
                }
            }
            GameMode.BOT -> game.resign(whiteResigns = config.playerWhite)
            GameMode.LOCAL -> game.resign(whiteResigns = game.position.whiteToMove)
        }
        onFinished()
    }

    fun offerOrAcceptDraw() {
        when (config.mode) {
            GameMode.ONLINE -> {
                val code = config.onlineCode ?: return
                val o = online ?: return
                viewModelScope.launch {
                    runCatching {
                        if (o.drawOfferBy != null && o.drawOfferBy != uid) {
                            chessApp.onlineRepository.finish(code, "1/2-1/2", "agreement")
                        } else {
                            chessApp.onlineRepository.offerDraw(code, uid ?: return@runCatching)
                            drawOfferSent = true
                            publish()
                        }
                    }.onFailure { message = it.message; publish() }
                }
            }
            GameMode.LOCAL -> { game.agreeDraw(); onFinished() }
            GameMode.BOT -> {
                // The bot accepts a draw only when it is not clearly better; keep it simple and decline otherwise.
                viewModelScope.launch {
                    val eval = runCatching { engine.search(game.uciPositionCommand(), "movetime 400") }.getOrNull()
                    val cp = eval?.lines?.firstOrNull()?.score?.let { it.cp ?: if ((it.mate ?: 0) > 0) 1000 else -1000 } ?: 0
                    // The human offers on their own turn, so cp is from the human's view: decline if clearly worse.
                    if (cp < -50) {
                        message = "${bot?.name ?: "Computer"} declines the draw."
                    } else {
                        game.agreeDraw()
                        onFinished()
                    }
                    publish()
                }
            }
        }
    }

    fun declineDraw() {
        val code = config.onlineCode ?: return
        viewModelScope.launch { runCatching { chessApp.onlineRepository.declineDraw(code) } }
    }

    fun dismissMessage() {
        message = null
        publish()
    }

    // ---- move handling ----

    private fun playHuman(move: Move) {
        val expectedPly = game.ply
        if (!applyMove(move)) return
        if (config.mode == GameMode.ONLINE) {
            val code = config.onlineCode ?: return
            val me = uid ?: return
            viewModelScope.launch {
                try {
                    chessApp.onlineRepository.submitMove(code, me, move.uci, expectedPly)
                    game.result()?.let { r -> finishOnline(r) }
                } catch (e: Exception) {
                    // Roll back the optimistic move.
                    if (game.ply == expectedPly + 1 && game.lastMove == move) game.undo()
                    message = e.message ?: "Could not send move"
                    publish()
                }
            }
        }
        if (config.mode == GameMode.BOT) maybeBotMove()
    }

    private fun applyMove(move: Move): Boolean {
        val mover = game.position.whiteToMove
        if (!game.play(move)) return false
        if (timeControl.isTimed && game.ply > 1) {
            if (mover) whiteMs = whiteMs?.plus(timeControl.incrementMs) else blackMs = blackMs?.plus(timeControl.incrementMs)
        }
        clearSelection()
        hint = null
        if (game.isOver()) onFinished() else publish()
        return true
    }

    private fun maybeBotMove() {
        val b = bot ?: return
        if (game.isOver() || game.position.whiteToMove == config.playerWhite) return
        botJob?.cancel()
        thinking = true
        publish()
        botJob = viewModelScope.launch {
            try {
                delay(250) // a short pause makes instant replies feel less jarring
                val uci = engine.botMove(game, b)
                thinking = false
                if (uci != null) {
                    val m = Move.fromUci(uci)
                    if (!applyMove(m)) publish()
                } else publish()
            } catch (e: Exception) {
                thinking = false
                if (e is kotlinx.coroutines.CancellationException) throw e
                engineError = e.message ?: "Engine error"
                publish()
            }
        }
    }

    private fun clearSelection() {
        selected = null
        targets = emptySet()
    }

    // ---- clocks ----

    private fun startClock() {
        viewModelScope.launch {
            var last = System.currentTimeMillis()
            while (true) {
                delay(100)
                val now = System.currentTimeMillis()
                val dt = now - last
                last = now
                if (game.isOver() || game.ply < 2) continue
                val whiteToMove = game.position.whiteToMove
                if (whiteToMove) whiteMs = (whiteMs ?: continue) - dt else blackMs = (blackMs ?: continue) - dt
                val left = if (whiteToMove) whiteMs!! else blackMs!!
                if (left <= 0) {
                    if (whiteToMove) whiteMs = 0 else blackMs = 0
                    game.timeout(whiteFlagged = whiteToMove)
                    onFinished()
                } else publish()
            }
        }
    }

    // ---- online sync ----

    private fun observeOnline() {
        val code = config.onlineCode ?: return
        viewModelScope.launch {
            chessApp.onlineRepository.observe(code)
                .catch { message = it.message; publish() }
                .collect { remote ->
                    online = remote
                    if (remote == null) {
                        message = "This game no longer exists."
                        publish()
                        return@collect
                    }
                    // Apply any moves we do not have yet.
                    if (remote.moves.size > game.ply && remote.moves.take(game.ply) == game.moves.map { it.uci }) {
                        for (u in remote.moves.drop(game.ply)) {
                            if (!game.playUci(u)) break
                        }
                        clearSelection()
                        hint = null
                    }
                    if (remote.status == "finished" && !game.isOver()) applyRemoteResult(remote)
                    if (remote.drawOfferBy == null) drawOfferSent = false
                    if (game.isOver()) onFinished() else publish()
                }
        }
    }

    private fun applyRemoteResult(remote: OnlineGame) {
        val outcome = remote.result?.let { Outcome.fromPgn(it) } ?: return
        when (remote.reason) {
            "resignation" -> game.resign(whiteResigns = outcome == Outcome.BLACK_WINS)
            "agreement" -> game.agreeDraw()
            "timeout" -> game.timeout(whiteFlagged = outcome == Outcome.BLACK_WINS)
            else -> game.abandon(winnerIsWhite = when (outcome) {
                Outcome.WHITE_WINS -> true
                Outcome.BLACK_WINS -> false
                Outcome.DRAW -> null
            })
        }
    }

    private fun finishOnline(r: GameResult) {
        val code = config.onlineCode ?: return
        viewModelScope.launch {
            runCatching { chessApp.onlineRepository.finish(code, r.outcome.pgn, r.reason.name.lowercase(Locale.ROOT)) }
        }
    }

    // ---- finishing & saving ----

    private fun onFinished() {
        botJob?.cancel()
        thinking = false
        clearSelection()
        hint = null
        if (savedId == null && game.ply > 0) {
            val id = UUID.randomUUID().toString()
            savedId = id
            val names = names()
            val result = game.result()?.outcome?.pgn ?: "*"
            val headers = mapOf(
                "Event" to when (config.mode) { GameMode.BOT -> "Game vs computer"; GameMode.ONLINE -> "Online game"; GameMode.LOCAL -> "Pass and play" },
                "Date" to SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date()),
                "White" to names.first,
                "Black" to names.second,
                "Result" to result,
            )
            chessApp.gameStore.save(
                SavedGame(id, game.toPgn(headers), names.first, names.second, result, config.mode.name.lowercase(Locale.ROOT), System.currentTimeMillis()),
            )
        }
        publish()
    }

    private fun names(): Pair<String, String> = when (config.mode) {
        GameMode.BOT -> {
            val me = chessApp.authManager.user.value?.name ?: "You"
            val b = bot?.let { "${it.name} (${it.elo})" } ?: "Computer"
            if (config.playerWhite) me to b else b to me
        }
        GameMode.ONLINE -> (online?.whiteName ?: "White") to (online?.blackName ?: "Black")
        GameMode.LOCAL -> "White" to "Black"
    }

    private fun defaultFlipped(): Boolean = when (config.mode) {
        GameMode.BOT -> !config.playerWhite
        GameMode.ONLINE -> online?.colorOf(uid ?: "") == false
        GameMode.LOCAL -> false
    }

    private fun publish() {
        val pos = game.position
        val result = game.result()
        val names = names()
        val o = online
        val capturedByWhite = ArrayList<Int>()
        val capturedByBlack = ArrayList<Int>()
        run {
            val initial = mapOf(PieceType.PAWN to 8, PieceType.KNIGHT to 2, PieceType.BISHOP to 2, PieceType.ROOK to 2, PieceType.QUEEN to 1)
            for (white in listOf(true, false)) {
                val counts = HashMap<Int, Int>()
                for (i in 0 until 64) {
                    val p = pos.pieceAt(i)
                    if (p != Piece.EMPTY && Piece.isWhite(p) == white) counts.merge(Piece.type(p), 1, Int::plus)
                }
                for ((type, n) in initial) {
                    val missing = n - (counts[type] ?: 0)
                    // Promotions can make this negative; clamp.
                    repeat(maxOf(0, missing)) {
                        // Pieces of colour `white` that were captured are credited to the opponent.
                        val glyphPiece = Piece.make(type, white)
                        if (white) capturedByBlack.add(glyphPiece) else capturedByWhite.add(glyphPiece)
                    }
                }
            }
        }
        val order = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)
        val sortKey: (Int) -> Int = { order.indexOf(Piece.type(it)) }
        _state.value = GameUiState(
            position = pos,
            lastMove = game.lastMove,
            sans = game.sans.toList(),
            selected = selected,
            targets = targets,
            pendingPromotion = pendingPromotion,
            result = result,
            thinking = thinking,
            hint = hint,
            flipped = flippedOverride ?: defaultFlipped(),
            whiteName = names.first,
            blackName = names.second,
            whiteMs = whiteMs?.coerceAtLeast(0),
            blackMs = blackMs?.coerceAtLeast(0),
            canMove = myTurn(),
            message = message,
            savedGameId = savedId,
            waitingForOpponent = if (config.mode == GameMode.ONLINE && o != null && o.status == "waiting") o.code else null,
            drawOfferFromOpponent = o != null && o.drawOfferBy != null && o.drawOfferBy != uid && result == null,
            drawOfferSent = drawOfferSent,
            captured = capturedByWhite.sortedBy(sortKey) to capturedByBlack.sortedBy(sortKey),
            canUndo = config.mode != GameMode.ONLINE && game.ply > 0 && result == null,
            canHint = config.mode != GameMode.ONLINE && result == null,
            isOnline = config.mode == GameMode.ONLINE,
            engineError = engineError,
        )
    }

    override fun onCleared() {
        botJob?.cancel()
        hintJob?.cancel()
        engine.close()
        super.onCleared()
    }

    class Factory(private val app: Application, private val config: GameConfig) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(app, config) as T
    }
}

fun GameResult.describe(whiteName: String, blackName: String): String {
    val winner = when (outcome) {
        Outcome.WHITE_WINS -> "$whiteName wins"
        Outcome.BLACK_WINS -> "$blackName wins"
        Outcome.DRAW -> "Draw"
    }
    return "$winner — ${reason.label}"
}
