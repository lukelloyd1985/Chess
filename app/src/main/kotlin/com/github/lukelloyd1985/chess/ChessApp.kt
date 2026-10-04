package com.github.lukelloyd1985.chess

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
        initFirebase()
        authManager = AuthManager(this)
        gameStore = GameStore(this)
        onlineRepository = OnlineRepository(this)
    }

    /**
     * Firebase is configured from build-time values (see app/build.gradle.kts) rather than
     * google-services.json. If they are blank (e.g. a local build without the CI secrets),
     * it stays uninitialised and sign-in / online play report "not configured".
     */
    private fun initFirebase() {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() || BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_APPLICATION_ID.isBlank()
        ) return
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        val builder = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
        if (BuildConfig.FIREBASE_SENDER_ID.isNotBlank()) builder.setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
        FirebaseApp.initializeApp(this, builder.build())
    }
}
