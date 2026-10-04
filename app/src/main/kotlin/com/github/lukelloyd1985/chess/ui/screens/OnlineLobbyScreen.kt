package com.github.lukelloyd1985.chess.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lukelloyd1985.chess.auth.UserProfile
import com.github.lukelloyd1985.chess.data.OnlineGame
import com.github.lukelloyd1985.chess.data.OnlineRepository
import com.github.lukelloyd1985.chess.ui.theme.ChessColors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun OnlineLobbyScreen(
    user: UserProfile?,
    repo: OnlineRepository,
    configured: Boolean,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var color by remember { mutableStateOf("random") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var myGames by remember { mutableStateOf<List<OnlineGame>>(emptyList()) }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        if (!configured) return@LaunchedEffect
        repo.observeMyGames(uid).catch { error = it.message }.collect { myGames = it }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        ScreenHeader("Play a friend", onBack)

        if (user == null || !configured) {
            Text(
                if (!configured) "Online play needs Firebase. Set the FIREBASE_* build values (see README)."
                else "Sign in with Google to play friends online.",
                color = Muted,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        Card {
            Text("Create a game", fontWeight = FontWeight.Bold)
            Text("You'll get a 6-character code to send to your friend.", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf("white" to "White", "random" to "Random", "black" to "Black").forEach { (k, label) ->
                    FilterChip(selected = color == k, onClick = { color = k }, label = { Text(label) })
                }
            }
            Button(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val white = when (color) { "white" -> true; "black" -> false; else -> null }
                        onOpenGame(repo.createGame(user, white))
                    } catch (e: Exception) {
                        error = e.message
                    }
                    busy = false
                }
            }) { Text("Create game") }
        }

        Card(Modifier.padding(top = 12.dp)) {
            Text("Join with a code", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                singleLine = true,
                placeholder = { Text("ABC123") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(enabled = !busy && code.length == 6, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        onOpenGame(repo.joinGame(user, code))
                    } catch (e: Exception) {
                        error = e.message
                    }
                    busy = false
                }
            }) { Text("Join game") }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp) }

        Text("Your games", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(myGames, key = { it.code }) { g ->
                Card(onClick = { onOpenGame(g.code) }) {
                    Text("${g.whiteName} vs ${g.blackName}", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "${g.code} · " + when (g.status) {
                            "waiting" -> "waiting for opponent"
                            "finished" -> "finished ${g.result ?: ""}"
                            else -> "in progress · ${g.moves.size} moves"
                        },
                        color = if (g.status == "active") ChessColors.Green else Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
