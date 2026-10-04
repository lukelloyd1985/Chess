package com.github.lukelloyd1985.chess.core

import kotlin.random.Random

/**
 * A computer opponent. Strength is shaped three ways: Stockfish's own
 * `UCI_Elo` limiter (1350+), a think-time budget, and for the lowest levels a
 * chance of deliberately choosing a weaker candidate move.
 */
data class Bot(
    val id: String,
    val name: String,
    val elo: Int,
    val blurb: String,
    /** Stockfish "Skill Level" 0..20; used when [limitElo] is false. */
    val skill: Int,
    /** Use UCI_LimitStrength/UCI_Elo with [elo] instead of Skill Level. */
    val limitElo: Boolean,
    val moveTimeMs: Int,
    val depth: Int?,
    /** Probability (0..1) of playing one of the top few candidate lines instead of the best. */
    val sloppiness: Double,
) {
    companion object {
        val ALL: List<Bot> = listOf(
            Bot("rookie", "Rookie Ron", 400, "Just learned how the pieces move.", 0, false, 50, 1, 0.65),
            Bot("casual", "Casual Cara", 700, "Plays for fun, hangs the odd piece.", 0, false, 80, 2, 0.4),
            Bot("club", "Clubber Cam", 1000, "Knows basic tactics.", 1, false, 100, 3, 0.2),
            Bot("student", "Student Sam", 1300, "Solid fundamentals.", 3, false, 150, 5, 0.08),
            Bot("improver", "Improver Ivy", 1500, "A decent club player.", 0, true, 300, null, 0.0),
            Bot("tactician", "Tactician Theo", 1800, "Sharp tactics, few blunders.", 0, true, 500, null, 0.0),
            Bot("expert", "Expert Eva", 2100, "Strong and consistent.", 0, true, 800, null, 0.0),
            Bot("master", "Master Max", 2500, "Master-level precision.", 0, true, 1200, null, 0.0),
            Bot("grandmaster", "Grandmaster Gia", 3190, "Full-strength Stockfish 19.", 0, true, 2000, null, 0.0),
        )

        fun byId(id: String): Bot = ALL.firstOrNull { it.id == id } ?: ALL[4]

        /** Clamp to the range Stockfish 19 accepts for UCI_Elo. */
        fun uciElo(bot: Bot): Int = bot.elo.coerceIn(1320, 3190)

        /**
         * Picks the move to play from engine candidates (best first). With probability
         * [Bot.sloppiness] a lower-ranked candidate is chosen.
         */
        fun pickMove(bot: Bot, candidates: List<String>, rng: Random = Random.Default): String? {
            if (candidates.isEmpty()) return null
            if (candidates.size == 1 || rng.nextDouble() >= bot.sloppiness) return candidates[0]
            return candidates[rng.nextInt(1, candidates.size)]
        }
    }
}

/** Game clock settings in milliseconds. A null limit means untimed. */
data class TimeControl(val label: String, val initialMs: Long?, val incrementMs: Long = 0) {
    val isTimed: Boolean get() = initialMs != null

    companion object {
        val PRESETS = listOf(
            TimeControl("Unlimited", null),
            TimeControl("1 min", 60_000),
            TimeControl("3 min", 180_000),
            TimeControl("3 | 2", 180_000, 2_000),
            TimeControl("5 min", 300_000),
            TimeControl("10 min", 600_000),
            TimeControl("15 | 10", 900_000, 10_000),
            TimeControl("30 min", 1_800_000),
        )

        fun fromLabel(label: String): TimeControl = PRESETS.firstOrNull { it.label == label } ?: PRESETS[0]
    }
}
