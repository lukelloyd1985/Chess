package com.github.lukelloyd1985.chess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lukelloyd1985.chess.core.Move
import com.github.lukelloyd1985.chess.core.Piece
import com.github.lukelloyd1985.chess.core.PieceType
import com.github.lukelloyd1985.chess.core.Position
import com.github.lukelloyd1985.chess.core.Sq
import com.github.lukelloyd1985.chess.ui.theme.ChessColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class BoardArrow(val from: Int, val to: Int, val color: Color)

/** Text glyph for a piece. The filled glyphs are used for both colours and tinted; U+FE0E forces text (not emoji) rendering. */
fun pieceGlyph(piece: Int): String {
    val g = when (Piece.type(piece)) {
        PieceType.KING -> "♚"
        PieceType.QUEEN -> "♛"
        PieceType.ROOK -> "♜"
        PieceType.BISHOP -> "♝"
        PieceType.KNIGHT -> "♞"
        PieceType.PAWN -> "♟"
        else -> ""
    }
    return g + "︎"
}

@Composable
fun ChessBoard(
    position: Position,
    flipped: Boolean,
    selected: Int?,
    targets: Set<Int>,
    lastMove: Move?,
    checkSquare: Int?,
    arrows: List<BoardArrow>,
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalBoardStyle.current
    Box(modifier.aspectRatio(1f)) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 8) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0 until 8) {
                        val file = if (flipped) 7 - col else col
                        val rank = if (flipped) row else 7 - row
                        val sq = Sq.make(file, rank)
                        BoardSquare(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            sq = sq,
                            piece = position.pieceAt(sq),
                            isLight = (file + rank) % 2 == 1,
                            isSelected = sq == selected,
                            isTarget = sq in targets,
                            isLastMove = lastMove != null && (sq == lastMove.from || sq == lastMove.to),
                            isCheck = sq == checkSquare,
                            fileLabel = if (style.showCoordinates && row == 7) ('a' + file).toString() else null,
                            rankLabel = if (style.showCoordinates && col == 0) ('1' + rank).toString() else null,
                            style = style,
                            onClick = { onSquareClick(sq) },
                        )
                    }
                }
            }
        }
        if (arrows.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 8f
                fun center(sq: Int): Offset {
                    val f = Sq.file(sq)
                    val r = Sq.rank(sq)
                    val col = if (flipped) 7 - f else f
                    val row = if (flipped) r else 7 - r
                    return Offset((col + 0.5f) * s, (row + 0.5f) * s)
                }
                for (a in arrows) {
                    val start = center(a.from)
                    val end = center(a.to)
                    val angle = atan2(end.y - start.y, end.x - start.x)
                    val headLen = s * 0.38f
                    val shaftEnd = Offset(end.x - cos(angle) * headLen * 0.6f, end.y - sin(angle) * headLen * 0.6f)
                    drawLine(a.color, start, shaftEnd, strokeWidth = s * 0.17f, cap = StrokeCap.Round)
                    val left = Offset(
                        end.x - cos(angle - 0.5f) * headLen,
                        end.y - sin(angle - 0.5f) * headLen,
                    )
                    val right = Offset(
                        end.x - cos(angle + 0.5f) * headLen,
                        end.y - sin(angle + 0.5f) * headLen,
                    )
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        close()
                    }
                    drawPath(path, a.color)
                }
            }
        }
    }
}

@Composable
private fun BoardSquare(
    modifier: Modifier,
    sq: Int,
    piece: Int,
    isLight: Boolean,
    isSelected: Boolean,
    isTarget: Boolean,
    isLastMove: Boolean,
    isCheck: Boolean,
    fileLabel: String?,
    rankLabel: String?,
    style: BoardStyle,
    onClick: () -> Unit,
) {
    val base = if (isLight) style.theme.light else style.theme.dark
    val labelColor = if (isLight) style.theme.dark else style.theme.light
    BoxWithConstraints(
        modifier
            .background(base)
            .then(if (isLastMove) Modifier.background(ChessColors.LastMove) else Modifier)
            .then(if (isSelected) Modifier.background(ChessColors.Selected) else Modifier)
            .then(if (isCheck) Modifier.background(ChessColors.Check) else Modifier)
            .clickable(onClick = onClick),
    ) {
        val sizeSp = with(LocalDensity.current) { maxHeight.toSp() }
        if (rankLabel != null) {
            Text(
                rankLabel,
                Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp),
                color = labelColor,
                fontSize = sizeSp * 0.22f,
                fontWeight = FontWeight.Bold,
            )
        }
        if (fileLabel != null) {
            Text(
                fileLabel,
                Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 1.dp),
                color = labelColor,
                fontSize = sizeSp * 0.22f,
                fontWeight = FontWeight.Bold,
            )
        }
        if (piece != Piece.EMPTY) {
            val white = Piece.isWhite(piece)
            if (style.art != null) {
                PieceIcon(piece, Modifier.align(Alignment.Center).fillMaxSize(0.92f), style)
            } else {
                // Artwork failed to load: fall back to plain text glyphs.
                Text(
                    pieceGlyph(piece),
                    Modifier.align(Alignment.Center),
                    style = TextStyle(
                        fontSize = sizeSp * 0.82f,
                        color = if (white) style.pieceColors.white else style.pieceColors.black,
                        shadow = Shadow(
                            color = if (white) Color(0xFF000000) else Color(0x99FFFFFF),
                            offset = Offset(0f, 0f),
                            blurRadius = 6f,
                        ),
                    ),
                )
            }
        }
        if (isTarget) {
            val capture = piece != Piece.EMPTY
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(if (capture) 0.96f else 0.3f)
                    .clip(CircleShape)
                    .then(
                        if (capture) Modifier.border(3.dp, Color(0x66000000), CircleShape)
                        else Modifier.background(Color(0x55000000)),
                    ),
            )
        }
    }
}
