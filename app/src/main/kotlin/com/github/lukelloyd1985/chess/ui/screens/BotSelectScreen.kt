package com.github.lukelloyd1985.chess.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lukelloyd1985.chess.core.Bot
import com.github.lukelloyd1985.chess.core.TimeControl
import com.github.lukelloyd1985.chess.ui.theme.ChessColors

@Composable
fun BotSelectScreen(onBack: () -> Unit, onStart: (GameConfig) -> Unit) {
    var botId by remember { mutableStateOf("improver") }
    var color by remember { mutableStateOf("white") } // white | black | random
    var tc by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Play the computer", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Bot.ALL, key = { it.id }) { bot ->
                val selected = bot.id == botId
                Card(
                    modifier = if (selected) Modifier.border(2.dp, ChessColors.Green, RoundedCornerShape(14.dp)) else Modifier,
                    onClick = { botId = bot.id },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(bot.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(bot.blurb, color = Muted, fontSize = 13.sp)
                        }
                        Text("${bot.elo}", fontWeight = FontWeight.Bold, color = ChessColors.Green, fontSize = 18.sp)
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Play as", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("white" to "White", "random" to "Random", "black" to "Black").forEach { (k, label) ->
                    FilterChip(selected = color == k, onClick = { color = k }, label = { Text(label) })
                }
            }
            Text("Time control", color = Muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeControl.PRESETS.forEachIndexed { i, p ->
                    FilterChip(selected = tc == i, onClick = { tc = i }, label = { Text(p.label) })
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    val white = when (color) {
                        "white" -> true
                        "black" -> false
                        else -> kotlin.random.Random.nextBoolean()
                    }
                    onStart(GameConfig(GameMode.BOT, botId = botId, playerWhite = white, timeControlIndex = tc))
                },
            ) { Text("Play", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
