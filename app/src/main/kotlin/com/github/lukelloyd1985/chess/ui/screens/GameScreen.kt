package com.github.lukelloyd1985.chess.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lukelloyd1985.chess.core.Piece
import com.github.lukelloyd1985.chess.core.PieceType
import com.github.lukelloyd1985.chess.ui.board.BoardArrow
import com.github.lukelloyd1985.chess.ui.board.ChessBoard
import com.github.lukelloyd1985.chess.ui.board.pieceGlyph
import com.github.lukelloyd1985.chess.ui.theme.ChessColors

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    onAnalyze: (String) -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmResign by remember { mutableStateOf(false) }
    var resultDismissed by remember(s.result) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(
            title = when {
                s.isOnline -> "Play a friend"
                else -> "Game"
            },
            onBack = onExit,
        )

        val topIsWhite = s.flipped
        PlayerBar(
            name = if (topIsWhite) s.whiteName else s.blackName,
            captured = if (topIsWhite) s.captured.first else s.captured.second,
            clockMs = if (topIsWhite) s.whiteMs else s.blackMs,
            active = s.result == null && (s.position.whiteToMove == topIsWhite),
            thinking = s.thinking && s.position.whiteToMove == topIsWhite,
        )

        val checkSq = if (s.position.isCheck()) s.position.kingSquare(s.position.whiteToMove) else null
        val arrows = s.hint?.let { listOf(BoardArrow(it.from, it.to, ChessColors.Hint)) }.orEmpty()
        ChessBoard(
            position = s.position,
            flipped = s.flipped,
            selected = s.selected,
            targets = s.targets,
            lastMove = s.lastMove,
            checkSquare = checkSq,
            arrows = arrows,
            onSquareClick = viewModel::onSquareClick,
            modifier = Modifier.fillMaxWidth(),
        )

        PlayerBar(
            name = if (topIsWhite) s.blackName else s.whiteName,
            captured = if (topIsWhite) s.captured.second else s.captured.first,
            clockMs = if (topIsWhite) s.blackMs else s.whiteMs,
            active = s.result == null && (s.position.whiteToMove != topIsWhite),
            thinking = false,
        )

        MoveStrip(s.sans)

        s.waitingForOpponent?.let { code ->
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Waiting for your friend…", color = Muted)
                Text(code, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ChessColors.Green, letterSpacing = 6.sp)
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Play chess with me! Open the Chess app → Play a friend → Join, and enter code $code")
                    }
                    context.startActivity(Intent.createChooser(send, "Invite a friend"))
                }) { Text("Share invite code") }
            }
        }

        s.engineError?.let {
            Text("Engine problem: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton("Flip", Icons.Default.SwapVert, onClick = viewModel::flipBoard)
            if (!s.isOnline) {
                ActionButton("Hint", Icons.Default.Lightbulb, enabled = s.canHint && s.canMove, onClick = viewModel::requestHint)
                ActionButton("Undo", Icons.Default.Undo, enabled = s.canUndo, onClick = viewModel::undo)
            }
            ActionButton("Draw", null, glyph = "½", enabled = s.result == null && !s.drawOfferSent && s.waitingForOpponent == null, onClick = viewModel::offerOrAcceptDraw)
            ActionButton("Resign", Icons.Default.Flag, enabled = s.result == null && s.waitingForOpponent == null, onClick = { confirmResign = true })
        }
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("Resign this game?") },
            confirmButton = { TextButton(onClick = { confirmResign = false; viewModel.resign() }) { Text("Resign") } },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("Cancel") } },
        )
    }

    s.pendingPromotion?.let {
        val white = s.position.whiteToMove
        AlertDialog(
            onDismissRequest = { viewModel.choosePromotion(null) },
            title = { Text("Promote to") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                        TextButton(onClick = { viewModel.choosePromotion(t) }) {
                            Text(
                                pieceGlyph(Piece.make(t, white)),
                                fontSize = 40.sp,
                                color = if (white) Color.White else Color(0xFF1B1B1B),
                                modifier = Modifier.background(ChessColors.BoardDark, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.choosePromotion(null) }) { Text("Cancel") } },
        )
    }

    if (s.drawOfferFromOpponent) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Draw offered") },
            text = { Text("Your opponent offers a draw.") },
            confirmButton = { TextButton(onClick = viewModel::offerOrAcceptDraw) { Text("Accept") } },
            dismissButton = { TextButton(onClick = viewModel::declineDraw) { Text("Decline") } },
        )
    }

    s.message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }

    val result = s.result
    if (result != null && !resultDismissed) {
        AlertDialog(
            onDismissRequest = { resultDismissed = true },
            title = { Text("Game over") },
            text = { Text(result.describe(s.whiteName, s.blackName)) },
            confirmButton = {
                Button(onClick = { s.savedGameId?.let(onAnalyze) ?: onExit() }) { Text("Analyze game") }
            },
            dismissButton = {
                OutlinedButton(onClick = onExit) { Text("Home") }
            },
        )
    }
}

@Composable
private fun PlayerBar(name: String, captured: List<Int>, clockMs: Long?, active: Boolean, thinking: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row {
                Text(
                    captured.joinToString("") { pieceGlyph(it) },
                    fontSize = 14.sp,
                    color = Muted,
                )
                if (thinking) Text("  thinking…", fontSize = 12.sp, color = ChessColors.Green)
            }
        }
        if (clockMs != null) {
            Text(
                formatClock(clockMs),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (clockMs < 10_000) ChessColors.Blunder else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) ChessColors.SurfaceHigh else ChessColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MoveStrip(sans: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(sans.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().background(ChessColors.Surface).horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sans.isEmpty()) Text("Moves will appear here", color = Muted, fontSize = 13.sp)
        sans.forEachIndexed { i, san ->
            if (i % 2 == 0) Text("${i / 2 + 1}.", color = Muted, fontSize = 13.sp)
            Text(san, fontSize = 13.sp, fontWeight = if (i == sans.size - 1) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
