package com.github.lukelloyd1985.chess.core

/**
 * Immutable chess position with full rules: legal move generation, castling,
 * en passant, promotion, check detection and FEN (de)serialisation.
 */
class Position private constructor(
    private val squares: IntArray,
    val whiteToMove: Boolean,
    /** Bitmask of [WHITE_KINGSIDE], [WHITE_QUEENSIDE], [BLACK_KINGSIDE], [BLACK_QUEENSIDE]. */
    val castling: Int,
    /** En passant target square, or -1. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(sq: Int): Int = squares[sq]

    fun kingSquare(white: Boolean): Int {
        val king = Piece.make(PieceType.KING, white)
        for (i in 0 until 64) if (squares[i] == king) return i
        return -1
    }

    fun isAttacked(sq: Int, byWhite: Boolean): Boolean {
        val f = Sq.file(sq)
        val r = Sq.rank(sq)
        // Pawns
        val pawn = Piece.make(PieceType.PAWN, byWhite)
        val pr = if (byWhite) r - 1 else r + 1
        for (df in intArrayOf(-1, 1)) {
            if (Sq.isValid(f + df, pr) && squares[Sq.make(f + df, pr)] == pawn) return true
        }
        // Knights
        val knight = Piece.make(PieceType.KNIGHT, byWhite)
        for (i in KNIGHT_DF.indices) {
            val nf = f + KNIGHT_DF[i]
            val nr = r + KNIGHT_DR[i]
            if (Sq.isValid(nf, nr) && squares[Sq.make(nf, nr)] == knight) return true
        }
        // King
        val king = Piece.make(PieceType.KING, byWhite)
        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            if (Sq.isValid(f + df, r + dr) && squares[Sq.make(f + df, r + dr)] == king) return true
        }
        // Sliders
        val bishop = Piece.make(PieceType.BISHOP, byWhite)
        val rook = Piece.make(PieceType.ROOK, byWhite)
        val queen = Piece.make(PieceType.QUEEN, byWhite)
        for (d in 0 until 8) {
            val df = DIR_DF[d]
            val dr = DIR_DR[d]
            val diagonal = d >= 4
            var nf = f + df
            var nr = r + dr
            while (Sq.isValid(nf, nr)) {
                val p = squares[Sq.make(nf, nr)]
                if (p != Piece.EMPTY) {
                    if (p == queen || (diagonal && p == bishop) || (!diagonal && p == rook)) return true
                    break
                }
                nf += df
                nr += dr
            }
        }
        return false
    }

    fun isCheck(): Boolean {
        val k = kingSquare(whiteToMove)
        return k >= 0 && isAttacked(k, !whiteToMove)
    }

    /** All legal moves for the side to move. */
    fun legalMoves(): List<Move> {
        val result = ArrayList<Move>(40)
        for (m in pseudoLegalMoves()) {
            val next = applyUnchecked(m)
            val k = next.kingSquare(whiteToMove)
            if (k < 0 || !next.isAttacked(k, !whiteToMove)) result.add(m)
        }
        return result
    }

    fun legalMovesFrom(sq: Int): List<Move> = legalMoves().filter { it.from == sq }

    fun isLegal(move: Move): Boolean = legalMoves().contains(move)

    /** Applies [move], which must be legal. */
    fun makeMove(move: Move): Position {
        require(isLegal(move)) { "Illegal move ${move.uci} in ${toFen()}" }
        return applyUnchecked(move)
    }

    private fun applyUnchecked(m: Move): Position {
        val b = squares.copyOf()
        val piece = b[m.from]
        val type = Piece.type(piece)
        val white = Piece.isWhite(piece)
        var captured = b[m.to]
        var newEp = -1
        var rights = castling

        b[m.from] = Piece.EMPTY
        if (type == PieceType.PAWN) {
            if (m.to == epSquare && captured == Piece.EMPTY && Sq.file(m.from) != Sq.file(m.to)) {
                val capSq = Sq.make(Sq.file(m.to), Sq.rank(m.from))
                captured = b[capSq]
                b[capSq] = Piece.EMPTY
            }
            if (Math.abs(Sq.rank(m.to) - Sq.rank(m.from)) == 2) {
                newEp = Sq.make(Sq.file(m.from), (Sq.rank(m.from) + Sq.rank(m.to)) / 2)
            }
        }
        b[m.to] = if (m.promotion != PieceType.NONE) Piece.make(m.promotion, white) else piece

        if (type == PieceType.KING) {
            if (Math.abs(Sq.file(m.to) - Sq.file(m.from)) == 2) {
                val rank = Sq.rank(m.from)
                if (Sq.file(m.to) == 6) {
                    b[Sq.make(7, rank)] = Piece.EMPTY
                    b[Sq.make(5, rank)] = Piece.make(PieceType.ROOK, white)
                } else {
                    b[Sq.make(0, rank)] = Piece.EMPTY
                    b[Sq.make(3, rank)] = Piece.make(PieceType.ROOK, white)
                }
            }
            rights = rights and (if (white) (WHITE_KINGSIDE or WHITE_QUEENSIDE).inv() else (BLACK_KINGSIDE or BLACK_QUEENSIDE).inv())
        }
        rights = rights and rightsMaskForSquare(m.from) and rightsMaskForSquare(m.to)

        val reset = type == PieceType.PAWN || captured != Piece.EMPTY
        return Position(
            b,
            !whiteToMove,
            rights,
            newEp,
            if (reset) 0 else halfmoveClock + 1,
            if (whiteToMove) fullmoveNumber else fullmoveNumber + 1,
        )
    }

    private fun rightsMaskForSquare(sq: Int): Int = when (sq) {
        Sq.A1 -> WHITE_QUEENSIDE.inv()
        Sq.H1 -> WHITE_KINGSIDE.inv()
        Sq.A8 -> BLACK_QUEENSIDE.inv()
        Sq.H8 -> BLACK_KINGSIDE.inv()
        Sq.E1 -> (WHITE_KINGSIDE or WHITE_QUEENSIDE).inv()
        Sq.E8 -> (BLACK_KINGSIDE or BLACK_QUEENSIDE).inv()
        else -> -1
    }

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(64)
        for (from in 0 until 64) {
            val p = squares[from]
            if (p == Piece.EMPTY || Piece.isWhite(p) != whiteToMove) continue
            val f = Sq.file(from)
            val r = Sq.rank(from)
            when (Piece.type(p)) {
                PieceType.PAWN -> genPawn(moves, from, f, r)
                PieceType.KNIGHT -> for (i in KNIGHT_DF.indices) addStep(moves, from, f + KNIGHT_DF[i], r + KNIGHT_DR[i])
                PieceType.BISHOP -> genSlider(moves, from, f, r, 4, 8)
                PieceType.ROOK -> genSlider(moves, from, f, r, 0, 4)
                PieceType.QUEEN -> genSlider(moves, from, f, r, 0, 8)
                PieceType.KING -> {
                    for (df in -1..1) for (dr in -1..1) if (df != 0 || dr != 0) addStep(moves, from, f + df, r + dr)
                    genCastling(moves, from)
                }
            }
        }
        return moves
    }

    private fun addStep(moves: MutableList<Move>, from: Int, nf: Int, nr: Int) {
        if (!Sq.isValid(nf, nr)) return
        val to = Sq.make(nf, nr)
        val target = squares[to]
        if (target == Piece.EMPTY || Piece.isWhite(target) != whiteToMove) moves.add(Move(from, to))
    }

    private fun genSlider(moves: MutableList<Move>, from: Int, f: Int, r: Int, dFrom: Int, dTo: Int) {
        for (d in dFrom until dTo) {
            var nf = f + DIR_DF[d]
            var nr = r + DIR_DR[d]
            while (Sq.isValid(nf, nr)) {
                val to = Sq.make(nf, nr)
                val target = squares[to]
                if (target == Piece.EMPTY) {
                    moves.add(Move(from, to))
                } else {
                    if (Piece.isWhite(target) != whiteToMove) moves.add(Move(from, to))
                    break
                }
                nf += DIR_DF[d]
                nr += DIR_DR[d]
            }
        }
    }

    private fun genPawn(moves: MutableList<Move>, from: Int, f: Int, r: Int) {
        val dir = if (whiteToMove) 1 else -1
        val startRank = if (whiteToMove) 1 else 6
        val promoRank = if (whiteToMove) 7 else 0
        val nr = r + dir
        if (nr !in 0..7) return

        fun add(to: Int) {
            if (Sq.rank(to) == promoRank) {
                for (t in intArrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) moves.add(Move(from, to, t))
            } else moves.add(Move(from, to))
        }

        val one = Sq.make(f, nr)
        if (squares[one] == Piece.EMPTY) {
            add(one)
            if (r == startRank) {
                val two = Sq.make(f, r + 2 * dir)
                if (squares[two] == Piece.EMPTY) moves.add(Move(from, two))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val to = Sq.make(nf, nr)
            val target = squares[to]
            if (target != Piece.EMPTY && Piece.isWhite(target) != whiteToMove) add(to)
            else if (target == Piece.EMPTY && to == epSquare) moves.add(Move(from, to))
        }
    }

    private fun genCastling(moves: MutableList<Move>, from: Int) {
        val white = whiteToMove
        val rank = if (white) 0 else 7
        if (from != Sq.make(4, rank)) return
        if (isAttacked(from, !white)) return
        val rook = Piece.make(PieceType.ROOK, white)
        val kingSide = if (white) WHITE_KINGSIDE else BLACK_KINGSIDE
        val queenSide = if (white) WHITE_QUEENSIDE else BLACK_QUEENSIDE
        if (castling and kingSide != 0 && squares[Sq.make(7, rank)] == rook &&
            squares[Sq.make(5, rank)] == Piece.EMPTY && squares[Sq.make(6, rank)] == Piece.EMPTY &&
            !isAttacked(Sq.make(5, rank), !white) && !isAttacked(Sq.make(6, rank), !white)
        ) moves.add(Move(from, Sq.make(6, rank)))
        if (castling and queenSide != 0 && squares[Sq.make(0, rank)] == rook &&
            squares[Sq.make(1, rank)] == Piece.EMPTY && squares[Sq.make(2, rank)] == Piece.EMPTY &&
            squares[Sq.make(3, rank)] == Piece.EMPTY &&
            !isAttacked(Sq.make(3, rank), !white) && !isAttacked(Sq.make(2, rank), !white)
        ) moves.add(Move(from, Sq.make(2, rank)))
    }

    /** True if neither side can possibly deliver mate (K vs K, K+minor vs K, same-colour bishops). */
    fun isInsufficientMaterial(): Boolean {
        val minors = ArrayList<Pair<Int, Int>>() // (type, square)
        for (i in 0 until 64) {
            val t = Piece.type(squares[i])
            when (t) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.KNIGHT, PieceType.BISHOP -> minors.add(t to i)
            }
        }
        if (minors.size <= 1) return true
        if (minors.all { it.first == PieceType.BISHOP }) {
            val colours = minors.map { (Sq.file(it.second) + Sq.rank(it.second)) % 2 }.toSet()
            return colours.size == 1
        }
        return false
    }

    /**
     * Key identifying the position for repetition purposes: placement, side to
     * move, castling rights and an en passant square only if capturable.
     */
    fun repetitionKey(): String {
        val sb = StringBuilder(fenPlacement())
        sb.append(if (whiteToMove) 'w' else 'b').append(castling)
        if (epSquare >= 0 && legalMoves().any { Piece.type(squares[it.from]) == PieceType.PAWN && it.to == epSquare }) {
            sb.append(Sq.name(epSquare))
        }
        return sb.toString()
    }

    private fun fenPlacement(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = squares[Sq.make(file, rank)]
                if (p == Piece.EMPTY) empty++ else {
                    if (empty > 0) sb.append(empty)
                    empty = 0
                    sb.append(Piece.toFenChar(p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        return sb.toString()
    }

    fun toFen(): String {
        val sb = StringBuilder(fenPlacement())
        sb.append(' ').append(if (whiteToMove) 'w' else 'b').append(' ')
        if (castling == 0) sb.append('-') else {
            if (castling and WHITE_KINGSIDE != 0) sb.append('K')
            if (castling and WHITE_QUEENSIDE != 0) sb.append('Q')
            if (castling and BLACK_KINGSIDE != 0) sb.append('k')
            if (castling and BLACK_QUEENSIDE != 0) sb.append('q')
        }
        sb.append(' ').append(if (epSquare >= 0) Sq.name(epSquare) else "-")
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    override fun toString(): String = toFen()

    companion object {
        const val WHITE_KINGSIDE = 1
        const val WHITE_QUEENSIDE = 2
        const val BLACK_KINGSIDE = 4
        const val BLACK_QUEENSIDE = 8

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val KNIGHT_DF = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
        private val KNIGHT_DR = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)

        // 0..3 orthogonal (rook), 4..7 diagonal (bishop)
        private val DIR_DF = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        private val DIR_DR = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        fun initial(): Position = fromFen(START_FEN)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 2) { "Bad FEN: '$fen'" }
            val squares = IntArray(64)
            val rows = parts[0].split('/')
            require(rows.size == 8) { "Bad FEN placement: '${parts[0]}'" }
            for ((i, row) in rows.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) file += c - '0' else {
                        require(file < 8) { "Bad FEN row '$row'" }
                        squares[Sq.make(file, rank)] = Piece.fromFenChar(c)
                        file++
                    }
                }
                require(file == 8) { "Bad FEN row '$row'" }
            }
            val white = when (parts[1]) {
                "w" -> true
                "b" -> false
                else -> throw IllegalArgumentException("Bad side to move in FEN")
            }
            var rights = 0
            val castlingField = parts.getOrElse(2) { "-" }
            for (c in castlingField) rights = rights or when (c) {
                'K' -> WHITE_KINGSIDE
                'Q' -> WHITE_QUEENSIDE
                'k' -> BLACK_KINGSIDE
                'q' -> BLACK_QUEENSIDE
                else -> 0
            }
            val ep = parts.getOrElse(3) { "-" }.let { if (it == "-") -1 else Sq.parse(it) }
            val half = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
            val full = parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1
            val pos = Position(squares, white, rights, ep, half, full)
            require(pos.kingSquare(true) >= 0 && pos.kingSquare(false) >= 0) { "FEN needs both kings" }
            return pos
        }
    }
}
