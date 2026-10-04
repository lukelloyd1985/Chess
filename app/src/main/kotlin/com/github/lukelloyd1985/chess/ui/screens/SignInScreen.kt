package com.github.lukelloyd1985.chess.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lukelloyd1985.chess.auth.AuthManager
import com.github.lukelloyd1985.chess.auth.SignInResult
import com.github.lukelloyd1985.chess.ui.theme.ChessColors
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun SignInScreen(auth: AuthManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("♞︎", fontSize = 96.sp, color = ChessColors.Green)
        Text("Chess", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(
            "Play the computer, challenge friends, and review every game with Stockfish 19.",
            textAlign = TextAlign.Center,
            color = Muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        )
        Button(
            onClick = {
                val activity = context.findActivity() ?: return@Button
                busy = true
                error = null
                scope.launch {
                    when (val r = auth.signInWithGoogle(activity)) {
                        is SignInResult.Failure -> error = r.message
                        else -> {}
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center) }
        if (!auth.isConfigured) {
            Text(
                "Sign-in isn't configured in this build (Appwrite project ID missing).",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        OutlinedButton(onClick = auth::continueAsGuest, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp)) {
            Text("Continue as guest (offline play only)")
        }
    }
}
