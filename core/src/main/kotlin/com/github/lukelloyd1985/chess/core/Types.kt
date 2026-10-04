package com.github.lukelloyd1985.chess.core

/** Piece types. Values double as the low bits of a piece code. */
object PieceType {
    const val NONE = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6
}

/**
 * A piece on the board is an Int: 0 = empty, 1..6 = white pawn..king,
 * 9..14 = black pawn..king.
 */
object Piece {
    const val EMPTY = 0
    const val BLACK_FLAG = 8

    fun make(type: Int, white: Boolean): Int = if (white) type else type or BLACK_FLAG
    fun type(piece: Int): Int = piece and 7
    fun isWhite(piece: Int): Boolean = piece != EMPTY && piece and BLACK_FLAG == 0
    fun isBlack(piece: Int): Boolean = piece and BLACK_FLAG != 0

    private const val LETTERS = "?PNBRQK"

    fun fromFenChar(c: Char): Int {
        val idx = LETTERS.indexOf(c.uppercaseChar())
        require(idx > 0) { "Bad piece char '$c'" }
        return make(idx, c.isUpperCase())
    }

    fun toFenChar(piece: Int): Char {
        val c = LETTERS[type(piece)]
        return if (isWhite(piece)) c else c.lowercaseChar()
    }

    fun letter(type: Int): Char = LETTERS[type]
}

/** Squares are 0..63 with a1 = 0, b1 = 1, ..., h1 = 7, a2 = 8, ..., h8 = 63. */
object Sq {
    fun make(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3
    fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7

    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"

    fun parse(name: String): Int {
        require(name.length == 2 && name[0] in 'a'..'h' && name[1] in '1'..'8') { "Bad square '$name'" }
        return make(name[0] - 'a', name[1] - '1')
    }

    const val A1 = 0
    const val C1 = 2
    const val D1 = 3
    const val E1 = 4
    const val F1 = 5
    const val G1 = 6
    const val H1 = 7
    const val A8 = 56
    const val C8 = 58
    const val D8 = 59
    const val E8 = 60
    const val F8 = 61
    const val G8 = 62
    const val H8 = 63
}

/** A move. [promotion] is a [PieceType] (KNIGHT..QUEEN) or 0. Castling is encoded as a king move of two files. */
data class Move(val from: Int, val to: Int, val promotion: Int = PieceType.NONE) {
    /** Long algebraic / UCI notation, e.g. "e2e4" or "e7e8q". */
    val uci: String
        get() = Sq.name(from) + Sq.name(to) + if (promotion != PieceType.NONE) Piece.letter(promotion).lowercaseChar().toString() else ""

    override fun toString(): String = uci

    companion object {
        fun fromUci(s: String): Move {
            require(s.length in 4..5) { "Bad UCI move '$s'" }
            val promo = if (s.length == 5) {
                val t = "?pnbrqk".indexOf(s[4].lowercaseChar())
                require(t in PieceType.KNIGHT..PieceType.QUEEN) { "Bad promotion in '$s'" }
                t
            } else PieceType.NONE
            return Move(Sq.parse(s.substring(0, 2)), Sq.parse(s.substring(2, 4)), promo)
        }
    }
}
