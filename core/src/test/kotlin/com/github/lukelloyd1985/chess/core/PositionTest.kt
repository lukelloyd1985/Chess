package com.github.lukelloyd1985.chess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {
    private fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        var n = 0L
        for (m in pos.legalMoves()) n += perft(pos.makeMove(m), depth - 1)
        return n
    }

    @Test fun perftStart() {
        assertEquals(20, perft(Position.initial(), 1))
        assertEquals(400, perft(Position.initial(), 2))
        assertEquals(8902, perft(Position.initial(), 3))
        assertEquals(197281, perft(Position.initial(), 4))
    }

    @Test fun perftKiwipete() {
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48, perft(p, 1))
        assertEquals(2039, perft(p, 2))
        assertEquals(97862, perft(p, 3))
    }

    @Test fun perftEndgameEnPassantPins() {
        val p = Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14, perft(p, 1))
        assertEquals(191, perft(p, 2))
        assertEquals(2812, perft(p, 3))
        assertEquals(43238, perft(p, 4))
    }

    @Test fun perftPromotions() {
        val p4 = Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6, perft(p4, 1))
        assertEquals(264, perft(p4, 2))
        assertEquals(9467, perft(p4, 3))
        val p5 = Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44, perft(p5, 1))
        assertEquals(1486, perft(p5, 2))
        assertEquals(62379, perft(p5, 3))
    }

    @Test fun fenRoundTrip() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Position.fromFen(fen).toFen())
        assertEquals(Position.START_FEN, Position.initial().toFen())
    }

    @Test fun sanBasics() {
        val g = ChessGame()
        for (s in listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O")) assertTrue(s, g.playSan(s))
        assertEquals("O-O", g.sans.last())
        assertEquals(Move.fromUci("e1g1"), g.moves.last())
    }

    @Test fun sanDisambiguationAndSuffixes() {
        val p = Position.fromFen("4k3/8/8/8/8/8/K7/R6R w - - 0 1")
        assertEquals("Rad1", San.toSan(p, Move.fromUci("a1d1")))
        assertEquals("Rhd1", San.toSan(p, Move.fromUci("h1d1")))
        val mate = Position.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w Q - 0 1")
        assertEquals("Ra8#", San.toSan(mate, Move.fromUci("a1a8")))
        val promo = Position.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        assertEquals("a8=Q", San.toSan(promo, Move.fromUci("a7a8q")))
    }

    @Test fun foolsMate() {
        val g = ChessGame()
        for (s in listOf("f3", "e5", "g4", "Qh4#")) assertTrue(g.playSan(s))
        val r = g.result()
        assertNotNull(r)
        assertEquals(Outcome.BLACK_WINS, r!!.outcome)
        assertEquals(EndReason.CHECKMATE, r.reason)
        assertFalse(g.play(Move.fromUci("a2a3")))
    }

    @Test fun stalemateAndInsufficient() {
        val g = ChessGame(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))
        assertEquals(EndReason.STALEMATE, g.result()!!.reason)
        val k = ChessGame(Position.fromFen("8/8/4k3/8/8/3K4/8/6B1 w - - 0 1"))
        assertEquals(EndReason.INSUFFICIENT_MATERIAL, k.result()!!.reason)
    }

    @Test fun threefoldRepetition() {
        val g = ChessGame()
        repeat(2) { for (s in listOf("Nf3", "Nf6", "Ng1", "Ng8")) assertTrue(g.playSan(s)) }
        assertEquals(EndReason.THREEFOLD, g.result()!!.reason)
    }

    @Test fun enPassantAndUndo() {
        val g = ChessGame()
        for (s in listOf("e4", "a6", "e5", "d5", "exd6")) assertTrue(g.playSan(s))
        assertEquals(PieceType.NONE, Piece.type(g.position.pieceAt(Sq.parse("d5"))))
        assertTrue(g.undo())
        assertEquals(Piece.make(PieceType.PAWN, false), g.position.pieceAt(Sq.parse("d5")))
    }

    @Test fun pgnRoundTrip() {
        val g = ChessGame()
        for (s in listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6")) g.playSan(s)
        val text = g.toPgn(mapOf("White" to "Alice", "Black" to "Bob"))
        val parsed = Pgn.parse(text)
        assertEquals("Alice", parsed.headers["White"])
        assertEquals(g.sans, parsed.game.sans)
        assertEquals(g.position.toFen(), parsed.game.position.toFen())
    }

    @Test fun pgnWithCommentsAndNags() {
        val text = """
            [Event "Test"]
            [Result "1-0"]

            1. e4 {best by test} e5 2. Nf3 (2. f4 exf4) Nc6 $1 3. Bb5 a6 1-0
        """.trimIndent()
        val parsed = Pgn.parse(text)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), parsed.game.sans)
        assertEquals("1-0", parsed.resultToken)
    }

    @Test fun pgnRejectsIllegalMove() {
        try {
            Pgn.parse("1. e4 e5 2. Ke3")
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun uciParsing() {
        val line = Uci.parseInfo("info depth 12 seldepth 18 multipv 2 score cp -34 nodes 123456 nps 800000 time 154 pv e7e5 g1f3 b8c6")
        assertNotNull(line)
        assertEquals(12, line!!.depth)
        assertEquals(2, line.multiPv)
        assertEquals(-34, line.score.cp)
        assertEquals(listOf("e7e5", "g1f3", "b8c6"), line.pv)
        val mate = Uci.parseInfo("info depth 5 score mate -2 pv h4h2")
        assertEquals(-2, mate!!.score.mate)
        assertNull(Uci.parseInfo("info string hello"))
        assertEquals("e2e4", Uci.parseBestMove("bestmove e2e4 ponder e7e5")!!.move)
        assertNull(Uci.parseBestMove("bestmove (none)")!!.move)
        assertEquals(listOf("e4", "e5", "Nf3"), Uci.pvToSan(Position.initial(), listOf("e2e4", "e7e5", "g1f3")))
    }

    @Test fun analysisClassification() {
        val g = ChessGame()
        for (s in listOf("e4", "e5", "Qh5")) g.playSan(s)
        val evals = listOf(
            PositionEval(EngineScore(30, null), "e2e4"),
            PositionEval(EngineScore(30, null), "e7e5"),
            PositionEval(EngineScore(35, null), "g1f3"),
            PositionEval(EngineScore(-300, null), null), // Qh5?? hangs nothing here, but eval drops for the test
        )
        val a = Analysis.build(g, evals)
        assertEquals(MoveQuality.BEST, a.moves[0].quality)
        assertEquals(MoveQuality.BEST, a.moves[1].quality)
        assertEquals(MoveQuality.BLUNDER, a.moves[2].quality)
        assertEquals("Nf3", a.moves[2].bestMoveSan)
        assertTrue(a.whiteAccuracy < 100.0 && a.blackAccuracy > 90.0)
    }

    @Test fun winPercentIsSymmetric() {
        assertEquals(50.0, Analysis.winPercent(0), 1e-9)
        assertTrue(Analysis.winPercent(300) > 70 && Analysis.winPercent(-300) < 30)
        assertEquals(100.0, Analysis.winPercent(200) + Analysis.winPercent(-200), 1e-9)
    }

    @Test fun terminalEval() {
        val mated = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(0, PositionEval.terminal(mated)!!.whiteScore.cp)
        val matedBlack = Position.fromFen("R5k1/5ppp/8/8/8/8/8/4K3 b - - 0 1")
        assertTrue(PositionEval.terminal(matedBlack)!!.whiteScore.cp!! > 0)
    }

    @Test fun botsPickMove() {
        val bot = Bot.byId("rookie")
        assertEquals("a", Bot.pickMove(Bot.byId("master"), listOf("a", "b", "c")))
        val picks = (0 until 200).map { Bot.pickMove(bot, listOf("a", "b", "c"), kotlin.random.Random(it)) }.toSet()
        assertTrue(picks.size > 1)
    }
}
