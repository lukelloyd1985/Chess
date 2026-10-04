package com.github.lukelloyd1985.chess.core

data class PgnGame(val headers: Map<String, String>, val game: ChessGame, val resultToken: String?)

object Pgn {
    fun write(game: ChessGame, headers: Map<String, String> = emptyMap()): String {
        val result = game.result()?.outcome?.pgn ?: headers["Result"] ?: "*"
        val h = LinkedHashMap<String, String>()
        h["Event"] = headers["Event"] ?: "Casual Game"
        h["Site"] = headers["Site"] ?: "Chess App"
        h["Date"] = headers["Date"] ?: "????.??.??"
        h["Round"] = headers["Round"] ?: "-"
        h["White"] = headers["White"] ?: "White"
        h["Black"] = headers["Black"] ?: "Black"
        h["Result"] = result
        for ((k, v) in headers) if (k !in h) h[k] = v
        if (game.startPosition.toFen() != Position.START_FEN) {
            h["SetUp"] = "1"
            h["FEN"] = game.startPosition.toFen()
        }
        val sb = StringBuilder()
        for ((k, v) in h) sb.append('[').append(k).append(" \"").append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n")
        sb.append('\n')

        val tokens = ArrayList<String>()
        var number = game.startPosition.fullmoveNumber
        var white = game.startPosition.whiteToMove
        for ((i, san) in game.sans.withIndex()) {
            if (white) tokens.add("$number.") else if (i == 0) tokens.add("$number...")
            tokens.add(san)
            if (!white) number++
            white = !white
        }
        tokens.add(result)
        var line = StringBuilder()
        for (t in tokens) {
            if (line.isNotEmpty() && line.length + 1 + t.length > 80) {
                sb.append(line).append('\n')
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(t)
        }
        sb.append(line).append('\n')
        return sb.toString()
    }

    /** Parses the first game in [text]. Throws [IllegalArgumentException] on illegal or unreadable moves. */
    fun parse(text: String): PgnGame {
        val headers = LinkedHashMap<String, String>()
        val tagRegex = Regex("""^\s*\[(\w+)\s+"((?:[^"\\]|\\.)*)"\]\s*$""")
        val movetext = StringBuilder()
        var seenMoves = false
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("%")) continue
            val m = tagRegex.matchEntire(line)
            if (m != null && !seenMoves) {
                headers[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
            } else if (line.isNotEmpty()) {
                if (line.startsWith("[") && seenMoves) break // next game's headers
                seenMoves = true
                movetext.append(line).append(' ')
            }
        }
        var start = Position.initial()
        headers["FEN"]?.let { start = Position.fromFen(it) }
        val game = ChessGame(start)

        val cleaned = stripComments(movetext.toString())
        var resultToken: String? = null
        for (token in cleaned.split(Regex("\\s+"))) {
            if (token.isEmpty()) continue
            if (token in setOf("1-0", "0-1", "1/2-1/2", "*")) {
                resultToken = token
                break
            }
            val san = token.replace(Regex("""^\d+\.+"""), "")
            if (san.isEmpty() || san.startsWith("$")) continue
            val move = San.parse(game.position, san)
                ?: throw IllegalArgumentException("Illegal or unreadable move '$san' at ply ${game.ply + 1}")
            game.play(move)
        }
        return PgnGame(headers, game, resultToken ?: headers["Result"])
    }

    private fun stripComments(s: String): String {
        val out = StringBuilder()
        var braces = 0
        var parens = 0
        var lineComment = false
        for (c in s) {
            when {
                lineComment -> if (c == '\n') lineComment = false
                c == '{' -> braces++
                c == '}' -> if (braces > 0) braces--
                braces > 0 -> {}
                c == '(' -> parens++
                c == ')' -> if (parens > 0) parens--
                parens > 0 -> {}
                c == ';' -> lineComment = true
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
