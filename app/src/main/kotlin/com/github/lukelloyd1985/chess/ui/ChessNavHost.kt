package com.github.lukelloyd1985.chess.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.lukelloyd1985.chess.ChessApp
import com.github.lukelloyd1985.chess.ui.board.ProvideBoardStyle
import com.github.lukelloyd1985.chess.ui.screens.AnalysisScreen
import com.github.lukelloyd1985.chess.ui.screens.AnalysisViewModel
import com.github.lukelloyd1985.chess.ui.screens.BotSelectScreen
import com.github.lukelloyd1985.chess.ui.screens.GameConfig
import com.github.lukelloyd1985.chess.ui.screens.GameMode
import com.github.lukelloyd1985.chess.ui.screens.GameScreen
import com.github.lukelloyd1985.chess.ui.screens.GameViewModel
import com.github.lukelloyd1985.chess.ui.screens.HomeScreen
import com.github.lukelloyd1985.chess.ui.screens.OnlineLobbyScreen
import com.github.lukelloyd1985.chess.ui.screens.SettingsScreen
import com.github.lukelloyd1985.chess.ui.screens.SignInScreen
import com.github.lukelloyd1985.chess.ui.screens.findActivity
import kotlinx.coroutines.launch

private fun gameRoute(c: GameConfig): String =
    "game?mode=${c.mode.name}&bot=${c.botId}&white=${c.playerWhite}&tc=${c.timeControlIndex}&code=${c.onlineCode ?: ""}"

@Composable
fun ChessNavHost(app: ChessApp) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by app.authManager.user.collectAsStateWithLifecycle()
    val guest by app.authManager.guest.collectAsStateWithLifecycle()
    val signedIn = user != null || guest

    // Move between the sign-in and home screens as the auth state changes.
    LaunchedEffect(signedIn) {
        val target = if (signedIn) "home" else "signin"
        if (nav.currentDestination?.route != target) {
            nav.navigate(target) { popUpTo(0) { inclusive = true } }
        }
    }

    val settings by app.settings.state.collectAsStateWithLifecycle()

    ProvideBoardStyle(settings) {
    NavHost(navController = nav, startDestination = if (signedIn) "home" else "signin") {
        composable("signin") { SignInScreen(app.authManager) }

        composable("home") {
            HomeScreen(
                user = user,
                store = app.gameStore,
                onPlayBot = { nav.navigate("bots") },
                onPlayFriend = { nav.navigate("lobby") },
                onPassAndPlay = { nav.navigate(gameRoute(GameConfig(GameMode.LOCAL))) },
                onOpenAnalysis = { nav.navigate("analysis/$it") },
                onSignOut = {
                    val activity = context.findActivity()
                    if (activity != null) scope.launch { app.authManager.signOut(activity) }
                },
                onOpenSettings = { nav.navigate("settings") },
                onDeleteAccount = {
                    val activity = context.findActivity()
                    if (activity != null) scope.launch {
                        runCatching { app.authManager.deleteAccount(activity) }
                            .onFailure { Toast.makeText(context, it.message ?: "Could not delete account", Toast.LENGTH_LONG).show() }
                    }
                },
            )
        }

        composable("settings") {
            SettingsScreen(app.settings, onBack = { nav.popBackStack() })
        }

        composable("bots") {
            BotSelectScreen(onBack = { nav.popBackStack() }, onStart = { nav.navigate(gameRoute(it)) })
        }

        composable("lobby") {
            OnlineLobbyScreen(
                user = user,
                repo = app.onlineRepository,
                configured = app.isBackendConfigured,
                onBack = { nav.popBackStack() },
                onOpenGame = { code -> nav.navigate(gameRoute(GameConfig(GameMode.ONLINE, onlineCode = code))) },
            )
        }

        composable(
            "game?mode={mode}&bot={bot}&white={white}&tc={tc}&code={code}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "LOCAL" },
                navArgument("bot") { type = NavType.StringType; defaultValue = "improver" },
                navArgument("white") { type = NavType.BoolType; defaultValue = true },
                navArgument("tc") { type = NavType.IntType; defaultValue = 0 },
                navArgument("code") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val a = entry.arguments
            val config = GameConfig(
                mode = GameMode.valueOf(a?.getString("mode") ?: "LOCAL"),
                botId = a?.getString("bot") ?: "improver",
                playerWhite = a?.getBoolean("white") ?: true,
                timeControlIndex = a?.getInt("tc") ?: 0,
                onlineCode = a?.getString("code")?.takeIf { it.isNotEmpty() },
            )
            val vm: GameViewModel = viewModel(factory = GameViewModel.Factory(app, config))
            GameScreen(
                viewModel = vm,
                onExit = { nav.popBackStack("home", inclusive = false) },
                onAnalyze = { id ->
                    nav.navigate("analysis/$id") { popUpTo("home") }
                },
            )
        }

        composable("analysis/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: AnalysisViewModel = viewModel(factory = AnalysisViewModel.Factory(app, id))
            AnalysisScreen(vm, onBack = { nav.popBackStack() })
        }
    }
    }
}
