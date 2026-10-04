package com.github.lukelloyd1985.chess.core

/** Standard Algebraic Notation conversion. */
object San {
    /** SAN of a legal [move] in [pos], including "+" / "#" suffix. */
    fun toSan(pos: Position, move: Move): String {
        val next = pos.makeMove(move)
        val suffix = if (next.isCheck()) (if (next.legalMoves().isEmpty()) "#" else "+") else ""
        return base(pos, move, pos.legalMoves()) + suffix
    }

    private fun base(pos: Position, move: Move, legal: List<Move>): String {
        val piece = pos.pieceAt(move.from)
        val type = Piece.type(piece)
        if (type == PieceType.KING && Math.abs(Sq.file(move.to) - Sq.file(move.from)) == 2) {
            return if (Sq.file(move.to) == 6) "O-O" else "O-O-O"
        }
        val isCapture = pos.pieceAt(move.to) != Piece.EMPTY ||
            (type == PieceType.PAWN && Sq.file(move.from) != Sq.file(move.to))
        val sb = StringBuilder()
        if (type == PieceType.PAWN) {
            if (isCapture) sb.append('a' + Sq.file(move.from))
        } else {
            sb.append(Piece.letter(type))
            val others = legal.filter { it != move && it.to == move.to && pos.pieceAt(it.from) == piece }
            if (others.isNotEmpty()) {
                val sameFile = others.any { Sq.file(it.from) == Sq.file(move.from) }
                val sameRank = others.any { Sq.rank(it.from) == Sq.rank(move.from) }
                if (!sameFile) sb.append('a' + Sq.file(move.from))
                else if (!sameRank) sb.append('1' + Sq.rank(move.from))
                else sb.append(Sq.name(move.from))
            }
        }
        if (isCapture) sb.append('x')
        sb.append(Sq.name(move.to))
        if (move.promotion != PieceType.NONE) sb.append('=').append(Piece.letter(move.promotion))
        return sb.toString()
    }

    /** Parses SAN (check marks and annotations tolerated). Returns null if no legal move matches. */
    fun parse(pos: Position, text: String): Move? {
        val cleaned = text.trim().replace("0", "O").trimEnd('+', '#', '!', '?')
        if (cleaned.isEmpty()) return null
        val legal = pos.legalMoves()
        val exact = legal.firstOrNull { base(pos, it, legal) == cleaned }
        if (exact != null) return exact
        // Lenient: ignore capture marks and '=' in promotions.
        val loose = cleaned.replace("x", "").replace("=", "")
        return legal.firstOrNull { base(pos, it, legal).replace("x", "").replace("=", "") == loose }
    }
}
