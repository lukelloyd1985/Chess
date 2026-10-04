package com.github.lukelloyd1985.chess.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.lukelloyd1985.chess.ChessApp
import com.github.lukelloyd1985.chess.core.Analysis
import com.github.lukelloyd1985.chess.core.ChessGame
import com.github.lukelloyd1985.chess.core.GameAnalysis
import com.github.lukelloyd1985.chess.core.Pgn
import com.github.lukelloyd1985.chess.core.PositionEval
import com.github.lukelloyd1985.chess.engine.StockfishEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** How long the engine thinks about each position. */
enum class AnalysisDepth(val label: String, val moveTimeMs: Int) {
    QUICK("Quick", 150),
    STANDARD("Standard", 500),
    DEEP("Deep", 2000),
}

data class AnalysisUiState(
    val title: String = "",
    val game: ChessGame? = null,
    val error: String? = null,
    val current: Int = 0, // position index: 0 = start
    val evals: List<PositionEval?> = emptyList(),
    val analysis: GameAnalysis? = null,
    val running: Boolean = false,
    val depth: AnalysisDepth = AnalysisDepth.STANDARD,
    val white: String = "White",
    val black: String = "Black",
)

/**
 * Runs Stockfish over every position of a saved game. There is no cap on the
 * number or length of games that can be analysed.
 */
class AnalysisViewModel(app: Application, private val gameId: String) : AndroidViewModel(app) {
    private val chessApp = app as ChessApp
    private val engine = StockfishEngine(app)
    private var job: Job? = null

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state

    init {
        val saved = chessApp.gameStore.get(gameId)
        if (saved == null) {
            _state.value = AnalysisUiState(error = "Game not found")
        } else {
            try {
                val parsed = Pgn.parse(saved.pgn)
                _state.value = AnalysisUiState(
                    title = saved.title,
                    game = parsed.game,
                    current = parsed.game.ply,
                    evals = List(parsed.game.ply + 1) { null },
                    white = saved.white,
                    black = saved.black,
                )
                start(AnalysisDepth.STANDARD)
            } catch (e: Exception) {
                _state.value = AnalysisUiState(error = e.message ?: "Could not read this game")
            }
        }
    }

    fun start(depth: AnalysisDepth) {
        val game = _state.value.game ?: return
        job?.cancel()
        _state.value = _state.value.copy(
            running = true, depth = depth, analysis = null, error = null,
            evals = List(game.ply + 1) { null },
        )
        job = viewModelScope.launch {
            try {
                val results = arrayOfNulls<PositionEval>(game.ply + 1)
                for (i in 0..game.ply) {
                    val pos = game.positions[i]
                    val eval = PositionEval.terminal(pos) ?: run {
                        val r = engine.search("position fen ${pos.toFen()}", "movetime ${depth.moveTimeMs}", multiPv = 3)
                        val top = r.lines.firstOrNull() ?: error("Engine returned no evaluation")
                        val score = top.score.toWhite(pos.whiteToMove)
                        PositionEval(
                            whiteScore = score,
                            bestMoveUci = r.best.move,
                            depth = top.depth,
                            lines = r.lines.map { it.copy(score = it.score.toWhite(pos.whiteToMove)) },
                        )
                    }
                    results[i] = eval
                    _state.value = _state.value.copy(evals = results.toList())
                }
                val analysis = Analysis.build(game, results.map { it!! })
                _state.value = _state.value.copy(running = false, analysis = analysis)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(running = false, error = e.message ?: "Analysis failed")
            }
        }
    }

    fun goTo(index: Int) {
        val game = _state.value.game ?: return
        _state.value = _state.value.copy(current = index.coerceIn(0, game.ply))
    }

    fun step(delta: Int) = goTo(_state.value.current + delta)

    override fun onCleared() {
        job?.cancel()
        engine.close()
        super.onCleared()
    }

    class Factory(private val app: Application, private val gameId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalysisViewModel(app, gameId) as T
    }
}
