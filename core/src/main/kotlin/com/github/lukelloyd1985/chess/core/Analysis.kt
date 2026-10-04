package com.github.lukelloyd1985.chess.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

enum class MoveQuality(val label: String, val symbol: String) {
    BEST("Best", "★"),
    EXCELLENT("Excellent", "!"),
    GOOD("Good", "✓"),
    INACCURACY("Inaccuracy", "?!"),
    MISTAKE("Mistake", "?"),
    BLUNDER("Blunder", "??"),
}

/** Engine evaluation of a position, always from White's point of view. */
data class PositionEval(
    val whiteScore: EngineScore,
    val bestMoveUci: String?,
    val depth: Int = 0,
    val lines: List<EngineLine> = emptyList(),
) {
    companion object {
        /** Evaluation of a position with no legal moves (no engine needed); null if the game goes on. */
        fun terminal(pos: Position): PositionEval? {
            if (pos.legalMoves().isNotEmpty()) return null
            val cp = when {
                !pos.isCheck() -> 0
                pos.whiteToMove -> -Analysis.DECISIVE_CP // White is mated
                else -> Analysis.DECISIVE_CP
            }
            return PositionEval(EngineScore(cp, null), null)
        }
    }
}

data class MoveAnalysis(
    val ply: Int, // 1-based
    val whiteMoved: Boolean,
    val san: String,
    val uci: String,
    val quality: MoveQuality,
    /** Win-probability points lost by the mover (0..100). */
    val winLoss: Double,
    val evalAfter: EngineScore, // white perspective
    val bestMoveUci: String?,
    val bestMoveSan: String?,
    val accuracy: Double,
)

data class GameAnalysis(
    /** Evaluation of every position: index 0 = start, index n = after ply n. */
    val evals: List<PositionEval>,
    val moves: List<MoveAnalysis>,
    val whiteAccuracy: Double,
    val blackAccuracy: Double,
) {
    fun count(white: Boolean, q: MoveQuality): Int = moves.count { it.whiteMoved == white && it.quality == q }
}

object Analysis {
    /** Centipawn value used for decided (mated) positions. */
    const val DECISIVE_CP = 100_000
    private const val MATE_CP = DECISIVE_CP

    /** Centipawn-equivalent of a score from White's view (mates saturate). */
    fun whiteCp(score: EngineScore): Int = score.cp ?: run {
        val m = score.mate!!
        if (m == 0) 0 else if (m > 0) MATE_CP - m else -MATE_CP - m
    }

    /** Lichess' logistic mapping from centipawns to win probability (0..100) for White. */
    fun winPercent(cp: Int): Double {
        val clamped = max(-1000, min(1000, cp))
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * clamped)) - 1.0)
    }

    fun winPercentWhite(score: EngineScore): Double = winPercent(whiteCp(score))

    /** Per-move accuracy from win% lost (Lichess formula). */
    fun accuracyFromLoss(loss: Double): Double =
        max(0.0, min(100.0, 103.1668 * exp(-0.04354 * max(0.0, loss)) - 3.1669 + 1.0))

    fun classify(loss: Double, playedBest: Boolean): MoveQuality = when {
        playedBest || loss <= 0.5 -> MoveQuality.BEST
        loss <= 2.0 -> MoveQuality.EXCELLENT
        loss <= 5.0 -> MoveQuality.GOOD
        loss <= 10.0 -> MoveQuality.INACCURACY
        loss <= 20.0 -> MoveQuality.MISTAKE
        else -> MoveQuality.BLUNDER
    }

    /**
     * Builds a full analysis from [evals], which must contain game.ply + 1 entries
     * (one for each position, starting position first).
     */
    fun build(game: ChessGame, evals: List<PositionEval>): GameAnalysis {
        require(evals.size == game.ply + 1) { "Need ${game.ply + 1} evals, got ${evals.size}" }
        val out = ArrayList<MoveAnalysis>(game.ply)
        for (i in 0 until game.ply) {
            val pos = game.positions[i]
            val whiteMoved = pos.whiteToMove
            val before = winPercentWhite(evals[i].whiteScore)
            val after = winPercentWhite(evals[i + 1].whiteScore)
            val loss = max(0.0, if (whiteMoved) before - after else after - before)
            val uci = game.moves[i].uci
            val best = evals[i].bestMoveUci
            val bestSan = best?.let {
                try { Move.fromUci(it).takeIf { m -> pos.isLegal(m) }?.let { m -> San.toSan(pos, m) } } catch (e: IllegalArgumentException) { null }
            }
            val quality = classify(loss, best == uci)
            out.add(
                MoveAnalysis(
                    ply = i + 1,
                    whiteMoved = whiteMoved,
                    san = game.sans[i],
                    uci = uci,
                    quality = quality,
                    winLoss = loss,
                    evalAfter = evals[i + 1].whiteScore,
                    bestMoveUci = best,
                    bestMoveSan = bestSan,
                    accuracy = accuracyFromLoss(loss),
                ),
            )
        }
        fun avg(white: Boolean): Double {
            val xs = out.filter { it.whiteMoved == white }
            return if (xs.isEmpty()) 100.0 else xs.sumOf { it.accuracy } / xs.size
        }
        return GameAnalysis(evals, out, avg(true), avg(false))
    }
}
