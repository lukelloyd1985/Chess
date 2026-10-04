package com.github.lukelloyd1985.chess.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lukelloyd1985.chess.auth.UserProfile
import com.github.lukelloyd1985.chess.data.GameStore
import com.github.lukelloyd1985.chess.data.SavedGame
import com.github.lukelloyd1985.chess.ui.theme.ChessColors

@Composable
fun HomeScreen(
    user: UserProfile?,
    store: GameStore,
    onPlayBot: () -> Unit,
    onPlayFriend: () -> Unit,
    onPassAndPlay: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    var games by remember { mutableStateOf(store.all()) }
    var showImport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chess", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(user?.let { "Signed in as ${it.name}" } ?: "Guest", color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onSignOut) { Text(if (user != null) "Sign out" else "Sign in") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Play the computer", "9 bots from 400 to 3190 Elo", onPlayBot)
            BigButton("Play a friend", "Create or join an online game with a code", onPlayFriend)
            BigButton("Pass & play", "Two players, one device", onPassAndPlay)
            BigButton("Analyze a PGN", "Import any game for an unlimited engine review", { showImport = true })
        }

        Text("Game history", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (games.isEmpty()) {
            Text("Finished and imported games show up here, ready for analysis.", color = Muted, fontSize = 13.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games, key = { it.id }) { g ->
                GameRow(g, onClick = { onOpenAnalysis(g.id) }, onDelete = {
                    store.delete(g.id)
                    games = store.all()
                })
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                try {
                    val saved = store.importPgn(text)
                    games = store.all()
                    showImport = false
                    onOpenAnalysis(saved.id)
                    null
                } catch (e: Exception) {
                    e.message ?: "Could not read that PGN"
                }
            },
            context = context,
        )
    }
}

@Composable
private fun BigButton(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChessColors.Green)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun GameRow(g: SavedGame, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${g.result} · ${g.mode}", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete game", tint = Muted) }
        }
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> String?, context: Context) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PGN") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    placeholder = { Text("Paste a PGN here") },
                )
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    error = null
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Paste from clipboard") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { error = onImport(text) }) { Text("Analyze") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
