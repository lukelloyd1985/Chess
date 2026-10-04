package com.github.lukelloyd1985.chess.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.github.lukelloyd1985.chess.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class UserProfile(val uid: String, val name: String, val email: String?, val photoUrl: String?)

sealed interface SignInResult {
    data class Success(val user: UserProfile) : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

/** Google sign-in through Credential Manager, exchanged for a Firebase Auth session. */
class AuthManager(private val appContext: Context) {
    /** True when google-services.json was supplied at build time. */
    val isConfigured: Boolean get() = FirebaseApp.getApps(appContext).isNotEmpty()

    private val _user = MutableStateFlow(currentUser())
    val user: StateFlow<UserProfile?> = _user

    private val _guest = MutableStateFlow(false)
    /** Guest mode: local games only (no online play). */
    val guest: StateFlow<Boolean> = _guest

    private fun currentUser(): UserProfile? {
        if (!isConfigured) return null
        val u = FirebaseAuth.getInstance().currentUser ?: return null
        return UserProfile(u.uid, u.displayName ?: u.email ?: "Player", u.email, u.photoUrl?.toString())
    }

    fun continueAsGuest() {
        _guest.value = true
    }

    private fun webClientId(): String {
        val generated = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return appContext.getString(if (generated != 0) generated else R.string.google_web_client_id)
    }

    suspend fun signInWithGoogle(activity: Activity): SignInResult {
        if (!isConfigured) {
            return SignInResult.Failure("Firebase is not configured. Add app/google-services.json (see README) and rebuild.")
        }
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId())
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                val profile = currentUser() ?: return SignInResult.Failure("Sign-in did not complete")
                _user.value = profile
                _guest.value = false
                SignInResult.Success(profile)
            } else {
                SignInResult.Failure("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            SignInResult.Failure(e.message ?: "Google sign-in failed")
        } catch (e: Exception) {
            SignInResult.Failure(e.message ?: "Sign-in failed")
        }
    }

    suspend fun signOut(activity: Activity) {
        if (isConfigured) FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
        _user.value = null
        _guest.value = false
    }
}
