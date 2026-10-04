package com.github.lukelloyd1985.chess.core

/** Engine score from the side-to-move's perspective: centipawns, or moves-to-mate (negative = being mated). */
data class EngineScore(val cp: Int?, val mate: Int?) {
    init {
        require((cp == null) != (mate == null)) { "Exactly one of cp / mate must be set" }
    }

    /** Converts to a white-perspective score given who is to move. */
    fun toWhite(whiteToMove: Boolean): EngineScore =
        if (whiteToMove) this else EngineScore(cp?.let { -it }, mate?.let { -it })
}

/** One principal variation reported by the engine via an `info` line. */
data class EngineLine(
    val depth: Int,
    val multiPv: Int,
    val score: EngineScore,
    val pv: List<String>,
    val nodes: Long? = null,
    val nps: Long? = null,
)

data class BestMove(val move: String?, val ponder: String?)

object Uci {
    /** Parses a UCI `info` line carrying a score and PV. Returns null for other lines. */
    fun parseInfo(line: String): EngineLine? {
        if (!line.startsWith("info ")) return null
        val t = line.trim().split(Regex("\\s+"))
        var depth = 0
        var multiPv = 1
        var score: EngineScore? = null
        var nodes: Long? = null
        var nps: Long? = null
        var pv: List<String> = emptyList()
        var i = 1
        while (i < t.size) {
            when (t[i]) {
                "depth" -> { depth = t.getOrNull(i + 1)?.toIntOrNull() ?: depth; i += 2 }
                "multipv" -> { multiPv = t.getOrNull(i + 1)?.toIntOrNull() ?: multiPv; i += 2 }
                "nodes" -> { nodes = t.getOrNull(i + 1)?.toLongOrNull(); i += 2 }
                "nps" -> { nps = t.getOrNull(i + 1)?.toLongOrNull(); i += 2 }
                "score" -> {
                    val kind = t.getOrNull(i + 1)
                    val value = t.getOrNull(i + 2)?.toIntOrNull()
                    if (value != null) {
                        score = when (kind) {
                            "cp" -> EngineScore(value, null)
                            "mate" -> EngineScore(null, value)
                            else -> null
                        }
                    }
                    i += 3
                    // optional lowerbound / upperbound
                    if (t.getOrNull(i) == "lowerbound" || t.getOrNull(i) == "upperbound") i++
                }
                "pv" -> { pv = t.subList(i + 1, t.size); i = t.size }
                else -> i++
            }
        }
        if (score == null || pv.isEmpty()) return null
        return EngineLine(depth, multiPv, score, pv, nodes, nps)
    }

    fun parseBestMove(line: String): BestMove? {
        if (!line.startsWith("bestmove")) return null
        val t = line.trim().split(Regex("\\s+"))
        val move = t.getOrNull(1)?.takeIf { it != "(none)" && it != "0000" }
        val ponder = t.indexOf("ponder").takeIf { it >= 0 }?.let { t.getOrNull(it + 1) }
        return BestMove(move, ponder)
    }

    /** Formats a score for display, e.g. "+1.25", "-0.30" or "#3". */
    fun formatScore(score: EngineScore): String = score.mate?.let { m ->
        if (m == 0) "#" else if (m > 0) "#$m" else "-#${-m}"
    } ?: run {
        val pawns = score.cp!! / 100.0
        String.format(java.util.Locale.US, "%+.2f", pawns)
    }

    /** Converts a PV of UCI moves into SAN starting from [pos]; stops at the first unplayable move. */
    fun pvToSan(pos: Position, pv: List<String>, maxMoves: Int = 8): List<String> {
        val out = ArrayList<String>()
        var cur = pos
        for (u in pv.take(maxMoves)) {
            val m = try { Move.fromUci(u) } catch (e: IllegalArgumentException) { break }
            if (!cur.isLegal(m)) break
            out.add(San.toSan(cur, m))
            cur = cur.makeMove(m)
        }
        return out
    }
}
