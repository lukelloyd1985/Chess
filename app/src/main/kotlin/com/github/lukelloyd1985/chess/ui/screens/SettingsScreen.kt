package com.github.lukelloyd1985.chess.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lukelloyd1985.chess.core.Move
import com.github.lukelloyd1985.chess.core.Piece
import com.github.lukelloyd1985.chess.core.PieceType
import com.github.lukelloyd1985.chess.core.Position
import com.github.lukelloyd1985.chess.core.Sq
import com.github.lukelloyd1985.chess.settings.AppSettings
import com.github.lukelloyd1985.chess.settings.BoardTheme
import com.github.lukelloyd1985.chess.settings.PieceColors
import com.github.lukelloyd1985.chess.settings.PieceSet
import com.github.lukelloyd1985.chess.ui.board.BoardStyle
import com.github.lukelloyd1985.chess.ui.board.ChessBoard
import com.github.lukelloyd1985.chess.ui.board.PieceArt
import com.github.lukelloyd1985.chess.ui.board.PieceIcon
import com.github.lukelloyd1985.chess.ui.theme.ChessColors

private val PreviewPosition = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 2 3")
private val PreviewLastMove = Move(Sq.parse("g1"), Sq.parse("f3"))

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val state by settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Appearance", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Live preview: the board below picks up the current style from LocalBoardStyle.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ChessBoard(
                    position = PreviewPosition,
                    flipped = false,
                    selected = null,
                    targets = emptySet(),
                    lastMove = PreviewLastMove,
                    checkSquare = null,
                    arrows = emptyList(),
                    onSquareClick = {},
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }

            SectionTitle("Board")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoardTheme.values().forEach { theme ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .selectedBorder(state.boardTheme == theme)
                                .clickable { settings.setBoardTheme(theme) },
                        ) {
                            val half = size.width / 2
                            val sq = Size(half, half)
                            drawRect(theme.light, Offset(0f, 0f), sq)
                            drawRect(theme.dark, Offset(half, 0f), sq)
                            drawRect(theme.dark, Offset(0f, half), sq)
                            drawRect(theme.light, Offset(half, half), sq)
                        }
                        Text(theme.label, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            SectionTitle("Pieces")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PieceSet.values().forEach { set ->
                    val art = remember(set) { PieceArt.load(context, set) }
                    // Show the set on the currently selected board colours and piece colours.
                    val style = BoardStyle(state.boardTheme, art, state.pieceColors, state.showCoordinates)
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .selectedBorder(state.pieceSet == set)
                            .clickable { settings.setPieceSet(set) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row {
                            for (white in listOf(true, false)) {
                                Box(
                                    Modifier.size(56.dp).background(if (white) state.boardTheme.dark else state.boardTheme.light).padding(4.dp),
                                ) { PieceIcon(Piece.make(PieceType.KNIGHT, white), Modifier.fillMaxSize(), style) }
                            }
                        }
                        Text(set.label, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            SectionTitle("Piece colours")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PieceColors.values().forEach { c ->
                    FilterChip(
                        selected = state.pieceColors == c,
                        onClick = { settings.setPieceColors(c) },
                        label = { Text(c.label) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show coordinates", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = state.showCoordinates, onCheckedChange = settings::setShowCoordinates)
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
}

private fun Modifier.selectedBorder(selected: Boolean): Modifier =
    if (selected) border(3.dp, ChessColors.Green, RoundedCornerShape(10.dp)) else border(1.dp, ChessColors.SurfaceHigh, RoundedCornerShape(10.dp))
