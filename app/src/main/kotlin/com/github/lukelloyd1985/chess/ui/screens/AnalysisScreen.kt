package com.github.lukelloyd1985.chess.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import com.github.lukelloyd1985.chess.core.TimeControl
import com.github.lukelloyd1985.chess.core.ChessGame
import com.github.lukelloyd1985.chess.core.Bot
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import com.github.lukelloyd1985.chess.core.Analysis
import com.github.lukelloyd1985.chess.core.EngineScore
import com.github.lukelloyd1985.chess.core.Move
import com.github.lukelloyd1985.chess.core.MoveAnalysis
import com.github.lukelloyd1985.chess.core.MoveQuality
import com.github.lukelloyd1985.chess.core.Uci
import com.github.lukelloyd1985.chess.ui.board.BoardArrow
import com.github.lukelloyd1985.chess.ui.board.ChessBoard
import com.github.lukelloyd1985.chess.ui.theme.ChessColors

fun qualityColor(q: MoveQuality): Color = when (q) {
    MoveQuality.BEST -> ChessColors.Best
    MoveQuality.EXCELLENT -> ChessColors.Excellent
    MoveQuality.GOOD -> ChessColors.Good
    MoveQuality.INACCURACY -> ChessColors.Inaccuracy
    MoveQuality.MISTAKE -> ChessColors.Mistake
    MoveQuality.BLUNDER -> ChessColors.Blunder
}

/** "+0.35", "#3", or a result string for decided positions. */
fun displayScore(score: EngineScore): String {
    val cp = score.cp
    if (cp != null && cp >= Analysis.DECISIVE_CP) return "1-0"
    if (cp != null && cp <= -Analysis.DECISIVE_CP) return "0-1"
    return Uci.formatScore(score)
}

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onBack: () -> Unit,
    /** Starts a game against the computer from the position currently shown. */
    onPlayFromHere: (GameConfig) -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var flipped by remember { mutableStateOf(false) }
    var showPlayDialog by remember { mutableStateOf(false) }
    val game = s.game

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title = "Game analysis", onBack = onBack, trailing = {
            IconButton(onClick = { flipped = !flipped }) { Icon(Icons.Default.SwapVert, contentDescription = "Flip board") }
        })

        if (game == null) {
            Text(s.error ?: "Loading…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            return@Column
        }

        val cur = s.current
        val pos = game.positions[cur]
        val eval = s.evals.getOrNull(cur)
        val analysis = s.analysis
        val moveInfo = analysis?.moves?.getOrNull(cur - 1)

        if (showPlayDialog) {
            PlayFromHereDialog(
                whiteToMove = pos.whiteToMove,
                onDismiss = { showPlayDialog = false },
                onStart = { botId, playerWhite, timeControlIndex ->
                    showPlayDialog = false
                    onPlayFromHere(
                        GameConfig(
                            mode = GameMode.BOT,
                            botId = botId,
                            playerWhite = playerWhite,
                            timeControlIndex = timeControlIndex,
                            startFen = pos.toFen(),
                        ),
                    )
                },
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(s.title, Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.SemiBold)
                if (s.running) {
                    val done = s.evals.count { it != null }
                    LinearProgressIndicator(
                        progress = { done.toFloat() / s.evals.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Text("Analysing position $done of ${s.evals.size}…", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = Muted)
                }
                s.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            }
            item {
                EvalBar(eval?.whiteScore, flipped, Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 8.dp))
                val bestArrow = eval?.bestMoveUci?.let { runCatching { Move.fromUci(it) }.getOrNull() }
                    ?.takeIf { pos.isLegal(it) }
                val arrows = listOfNotNull(bestArrow?.let { BoardArrow(it.from, it.to, ChessColors.Hint) })
                ChessBoard(
                    position = pos,
                    flipped = flipped,
                    selected = null,
                    targets = emptySet(),
                    lastMove = if (cur > 0) game.moves[cur - 1] else null,
                    checkSquare = if (pos.isCheck()) pos.kingSquare(pos.whiteToMove) else null,
                    arrows = arrows,
                    onSquareClick = {},
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            item {
                // Current move verdict + engine lines
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (moveInfo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QualityBadge(moveInfo.quality)
                            Text(
                                "  ${moveNumber(moveInfo)}${moveInfo.san} is ${moveInfo.quality.label.lowercase()}",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (moveInfo.bestMoveSan != null && moveInfo.quality != MoveQuality.BEST) {
                            Text("Best was ${moveInfo.bestMoveSan}", color = Muted, fontSize = 13.sp)
                        }
                    }
                    if (eval != null) {
                        Text("Eval ${displayScore(eval.whiteScore)}  ·  depth ${eval.depth}", color = Muted, fontSize = 13.sp)
                        eval.lines.forEach { line ->
                            val san = Uci.pvToSan(pos, line.pv, 6).joinToString(" ")
                            Row {
                                Text(
                                    displayScore(line.score),
                                    Modifier.width(56.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(san, fontSize = 13.sp, color = Muted)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { viewModel.goTo(0) }) { Text("⏮", fontSize = 20.sp) }
                    IconButton(onClick = { viewModel.step(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous move") }
                    IconButton(onClick = { viewModel.step(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next move") }
                    IconButton(onClick = { viewModel.goTo(game.ply) }) { Text("⏭", fontSize = 20.sp) }
                }
            }
            item {
                // A position with no legal moves (or an already-drawn one) can't be played on from.
                val canPlayFrom = remember(pos) { ChessGame(pos).result() == null }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = { showPlayDialog = true }, enabled = canPlayFrom) { Text("Play from here") }
                }
            }
            if (s.evals.any { it != null }) {
                item { EvalGraph(s.evals.map { it?.whiteScore }, cur, onSelect = viewModel::goTo) }
            }
            if (analysis != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccuracyCard(s.white, analysis.whiteAccuracy, Modifier.weight(1f))
                        AccuracyCard(s.black, analysis.blackAccuracy, Modifier.weight(1f))
                    }
                    SummaryTable(analysis.let { a -> MoveQuality.values().map { q -> Triple(q, a.count(true, q), a.count(false, q)) } })
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Depth:", color = Muted, fontSize = 13.sp)
                    AnalysisDepth.values().forEach { d ->
                        FilterChip(selected = s.depth == d, onClick = { viewModel.start(d) }, label = { Text(d.label) })
                    }
                }
            }
            // Move list
            val rows = (game.ply + 1) / 2
            items((0 until rows).toList()) { r ->
                val wi = r * 2
                val bi = r * 2 + 1
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${r + 1}.", Modifier.width(36.dp), color = Muted, fontSize = 14.sp)
                    MoveCell(Modifier.weight(1f), game.sans[wi], analysis?.moves?.getOrNull(wi), cur == wi + 1) { viewModel.goTo(wi + 1) }
                    if (bi < game.ply) {
                        MoveCell(Modifier.weight(1f), game.sans[bi], analysis?.moves?.getOrNull(bi), cur == bi + 1) { viewModel.goTo(bi + 1) }
                    } else Box(Modifier.weight(1f)) {}
                }
            }
            item { Box(Modifier.height(24.dp)) {} }
        }
    }
}

private fun moveNumber(m: MoveAnalysis): String = if (m.whiteMoved) "${(m.ply + 1) / 2}. " else "${m.ply / 2}... "

@Composable
private fun QualityBadge(q: MoveQuality) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(qualityColor(q)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(q.symbol, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun MoveCell(modifier: Modifier, san: String, info: MoveAnalysis?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) ChessColors.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(san, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
        if (info != null && info.quality != MoveQuality.BEST && info.quality != MoveQuality.EXCELLENT && info.quality != MoveQuality.GOOD) {
            Text(" ${info.quality.symbol}", color = qualityColor(info.quality), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EvalBar(score: EngineScore?, flipped: Boolean, modifier: Modifier) {
    // White's share of the bar, 0..1. Unflipped: white grows from the left.
    val white = ((score?.let { Analysis.winPercentWhite(it) } ?: 50.0) / 100.0).toFloat()
    Canvas(modifier.clip(RoundedCornerShape(4.dp))) {
        val w = size.width
        val whiteW = w * white
        drawRect(Color(0xFF403D39))
        drawRect(
            Color.White,
            topLeft = Offset(if (flipped) w - whiteW else 0f, 0f),
            size = androidx.compose.ui.geometry.Size(whiteW, size.height),
        )
    }
}

@Composable
private fun EvalGraph(scores: List<EngineScore?>, current: Int, onSelect: (Int) -> Unit) {
    val points = scores.map { it?.let { s -> Analysis.winPercentWhite(s) / 100.0 } }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF403D39))
            .pointerInput(points.size) {
                detectTapGestures { off ->
                    if (points.size > 1) onSelect((off.x / size.width * (points.size - 1)).roundToInt())
                }
            },
    ) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)
        val path = Path()
        path.moveTo(0f, h)
        var lastX = 0f
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = h - ((p ?: 0.5) * h).toFloat()
            path.lineTo(x, y)
            lastX = x
        }
        path.lineTo(lastX, h)
        path.close()
        drawPath(path, Color(0xFFEDEDED))
        // midline
        drawLine(Color(0x66000000), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1f)
        // current marker
        val cx = current * stepX
        drawLine(ChessColors.Green, Offset(cx, 0f), Offset(cx, h), strokeWidth = 3f)
    }
}

@Composable
private fun AccuracyCard(name: String, accuracy: Double, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, maxLines = 1, fontSize = 13.sp, color = Muted)
        Text("%.1f".format(accuracy), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ChessColors.Green)
        Text("accuracy", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun SummaryTable(rows: List<Triple<MoveQuality, Int, Int>>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { (q, w, b) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$w", Modifier.width(32.dp), fontWeight = FontWeight.Bold)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    QualityBadge(q)
                    Text("  ${q.label}", fontSize = 13.sp)
                }
                Text("$b", Modifier.width(32.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlayFromHereDialog(
    whiteToMove: Boolean,
    onDismiss: () -> Unit,
    onStart: (botId: String, playerWhite: Boolean, timeControlIndex: Int) -> Unit,
) {
    var botId by remember { mutableStateOf("improver") }
    // Default to playing the side that is to move in this position.
    var playWhite by remember { mutableStateOf(whiteToMove) }
    var timeControl by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play from this position") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Play as", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = playWhite, onClick = { playWhite = true }, label = { Text("White") })
                    FilterChip(selected = !playWhite, onClick = { playWhite = false }, label = { Text("Black") })
                }
                Text("Opponent", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Bot.ALL.forEach { bot ->
                    Row(
                        Modifier.fillMaxWidth().clickable { botId = bot.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = botId == bot.id, onClick = { botId = bot.id })
                        Text("${bot.name} (${bot.elo})")
                    }
                }
                Text("Time control", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeControl.PRESETS.forEachIndexed { i, tc ->
                        FilterChip(selected = timeControl == i, onClick = { timeControl = i }, label = { Text(tc.label) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onStart(botId, playWhite, timeControl) }) { Text("Play") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
