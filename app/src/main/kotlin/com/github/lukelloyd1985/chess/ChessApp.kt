package com.github.lukelloyd1985.chess

import android.app.Application
import com.github.lukelloyd1985.chess.auth.AuthManager
import com.github.lukelloyd1985.chess.data.GameStore
import com.github.lukelloyd1985.chess.data.OnlineRepository

/** Application class holding the app-wide singletons (no DI framework needed at this size). */
class ChessApp : Application() {
    lateinit var authManager: AuthManager
        private set
    lateinit var gameStore: GameStore
        private set
    lateinit var onlineRepository: OnlineRepository
        private set

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(this)
        gameStore = GameStore(this)
        onlineRepository = OnlineRepository(this)
    }
}
