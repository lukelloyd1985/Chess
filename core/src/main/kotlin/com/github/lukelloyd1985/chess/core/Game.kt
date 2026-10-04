package com.github.lukelloyd1985.chess.core

enum class Outcome(val pgn: String) {
    WHITE_WINS("1-0"), BLACK_WINS("0-1"), DRAW("1/2-1/2");

    companion object {
        fun fromPgn(s: String): Outcome? = values().firstOrNull { it.pgn == s }
    }
}

enum class EndReason(val label: String) {
    CHECKMATE("Checkmate"),
    STALEMATE("Stalemate"),
    INSUFFICIENT_MATERIAL("Insufficient material"),
    FIFTY_MOVE("50-move rule"),
    THREEFOLD("Threefold repetition"),
    RESIGNATION("Resignation"),
    TIMEOUT("Timeout"),
    AGREEMENT("Draw agreed"),
    ABANDONED("Abandoned"),
    UNKNOWN("Game over"),
}

data class GameResult(val outcome: Outcome, val reason: EndReason)

/**
 * A game in progress: the move list plus every position reached, with
 * automatic detection of mate, stalemate and the standard draw rules.
 */
class ChessGame(val startPosition: Position = Position.initial()) {
    private val positionList = mutableListOf(startPosition)
    private val moveList = mutableListOf<Move>()
    private val sanList = mutableListOf<String>()

    /** Result set from outside the rules (resign, timeout, agreed draw). */
    var forcedResult: GameResult? = null
        private set

    val position: Position get() = positionList.last()
    val moves: List<Move> get() = moveList
    val sans: List<String> get() = sanList
    val positions: List<Position> get() = positionList
    val lastMove: Move? get() = moveList.lastOrNull()
    val ply: Int get() = moveList.size

    fun legalMoves(): List<Move> = if (isOver()) emptyList() else position.legalMoves()

    /** Plays [move] if it is legal and the game is not over. */
    fun play(move: Move): Boolean {
        if (isOver() || !position.isLegal(move)) return false
        sanList.add(San.toSan(position, move))
        moveList.add(move)
        positionList.add(position.makeMove(move))
        return true
    }

    fun playUci(uci: String): Boolean = try {
        play(Move.fromUci(uci))
    } catch (e: IllegalArgumentException) {
        false
    }

    fun playSan(san: String): Boolean {
        val m = San.parse(position, san) ?: return false
        return play(m)
    }

    /** Takes back the last move; returns false if there is none. */
    fun undo(): Boolean {
        if (moveList.isEmpty()) return false
        moveList.removeAt(moveList.size - 1)
        sanList.removeAt(sanList.size - 1)
        positionList.removeAt(positionList.size - 1)
        forcedResult = null
        return true
    }

    fun resign(whiteResigns: Boolean) {
        if (!isOver()) forcedResult = GameResult(if (whiteResigns) Outcome.BLACK_WINS else Outcome.WHITE_WINS, EndReason.RESIGNATION)
    }

    fun agreeDraw() {
        if (!isOver()) forcedResult = GameResult(Outcome.DRAW, EndReason.AGREEMENT)
    }

    fun timeout(whiteFlagged: Boolean) {
        if (isOver()) return
        // A flagged side only loses if the opponent could still mate.
        val opponentCanMate = !hasInsufficientMaterialFor(!whiteFlagged)
        forcedResult = if (opponentCanMate) {
            GameResult(if (whiteFlagged) Outcome.BLACK_WINS else Outcome.WHITE_WINS, EndReason.TIMEOUT)
        } else GameResult(Outcome.DRAW, EndReason.TIMEOUT)
    }

    fun abandon(winnerIsWhite: Boolean?) {
        if (isOver()) return
        forcedResult = GameResult(
            when (winnerIsWhite) {
                true -> Outcome.WHITE_WINS
                false -> Outcome.BLACK_WINS
                null -> Outcome.DRAW
            },
            EndReason.ABANDONED,
        )
    }

    /** The game result, or null while the game is still in progress. */
    fun result(): GameResult? {
        forcedResult?.let { return it }
        val pos = position
        if (pos.legalMoves().isEmpty()) {
            return if (pos.isCheck()) {
                GameResult(if (pos.whiteToMove) Outcome.BLACK_WINS else Outcome.WHITE_WINS, EndReason.CHECKMATE)
            } else GameResult(Outcome.DRAW, EndReason.STALEMATE)
        }
        if (pos.isInsufficientMaterial()) return GameResult(Outcome.DRAW, EndReason.INSUFFICIENT_MATERIAL)
        if (pos.halfmoveClock >= 100) return GameResult(Outcome.DRAW, EndReason.FIFTY_MOVE)
        val key = pos.repetitionKey()
        var count = 0
        for (p in positionList) if (p.repetitionKey() == key) count++
        if (count >= 3) return GameResult(Outcome.DRAW, EndReason.THREEFOLD)
        return null
    }

    fun isOver(): Boolean = result() != null

    private fun hasInsufficientMaterialFor(white: Boolean): Boolean {
        var minors = 0
        for (i in 0 until 64) {
            val p = position.pieceAt(i)
            if (p == Piece.EMPTY || Piece.isWhite(p) != white) continue
            when (Piece.type(p)) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.KNIGHT, PieceType.BISHOP -> minors++
            }
        }
        return minors < 2
    }

    /** UCI "position" command arguments: `startpos moves ...` or `fen ... moves ...`. */
    fun uciPositionCommand(): String {
        val base = if (startPosition.toFen() == Position.START_FEN) "startpos" else "fen ${startPosition.toFen()}"
        return if (moveList.isEmpty()) "position $base" else "position $base moves ${moveList.joinToString(" ") { it.uci }}"
    }

    fun toPgn(headers: Map<String, String> = emptyMap()): String = Pgn.write(this, headers)
}
