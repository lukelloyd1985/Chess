package com.github.lukelloyd1985.chess

import android.app.Application
import android.content.Context
import com.github.lukelloyd1985.chess.crash.CrashHandler
import com.github.lukelloyd1985.chess.auth.AuthManager
import com.github.lukelloyd1985.chess.data.GameStore
import com.github.lukelloyd1985.chess.data.OnlineRepository
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Functions
import io.appwrite.services.Realtime

/** Application class holding the app-wide singletons (no DI framework needed at this size). */
class ChessApp : Application() {
    lateinit var authManager: AuthManager
        private set
    lateinit var gameStore: GameStore
        private set
    lateinit var onlineRepository: OnlineRepository
        private set

    /**
     * False while appwrite/appwrite.json still holds its placeholder project ID (or the ID is
     * blank). Sign-in and online play then report "not configured" instead of failing obscurely.
     */
    val isBackendConfigured: Boolean
        get() = BuildConfig.APPWRITE_PROJECT_ID.isNotBlank() &&
            !BuildConfig.APPWRITE_PROJECT_ID.startsWith("REPLACE")

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // As early as possible, so even a crash during app start-up shows the crash screen.
        CrashHandler.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // The crash screen runs in its own process; keep that process free of app start-up work.
        if (CrashHandler.isCrashProcess()) return
        val client = Client(this)
            .setEndpoint(BuildConfig.APPWRITE_ENDPOINT)
            .setProject(BuildConfig.APPWRITE_PROJECT_ID)
        val account = Account(client)
        val functions = Functions(client)
        authManager = AuthManager(isBackendConfigured, account, functions)
        gameStore = GameStore(this)
        onlineRepository = OnlineRepository(Databases(client), Realtime(client), functions)
    }
}
