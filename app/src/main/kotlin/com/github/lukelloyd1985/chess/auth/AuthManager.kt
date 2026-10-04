package com.github.lukelloyd1985.chess.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.github.lukelloyd1985.chess.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.appwrite.enums.ExecutionStatus
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Account
import io.appwrite.services.Functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

data class UserProfile(val uid: String, val name: String, val email: String?, val photoUrl: String?)

sealed interface SignInResult {
    data class Success(val user: UserProfile) : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

/**
 * Google sign-in through Credential Manager, bridged into a real Appwrite session: the Google ID
 * token is sent to the `maintenance` Function's /google-sign-in route (see
 * appwrite/functions/maintenance/src/googleSignIn.ts), which verifies it, creates the Appwrite
 * user on first sign-in and returns a one-time token; account.createSession turns that into a
 * session. No Appwrite-hosted page is ever shown.
 */
class AuthManager(
    /** False when Appwrite is not set up for this build (see ChessApp.isBackendConfigured). */
    val isConfigured: Boolean,
    private val account: Account,
    private val functions: Functions,
) {
    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user

    private val _guest = MutableStateFlow(false)

    /** Guest mode: local games only (no online play). */
    val guest: StateFlow<Boolean> = _guest

    init {
        // Seeds the state from an existing session (e.g. process restart while signed in).
        if (isConfigured) {
            CoroutineScope(Dispatchers.IO).launch { refreshCurrentUser() }
        }
    }

    private suspend fun refreshCurrentUser(photoUrl: String? = null) {
        _user.value = try {
            val u = account.get()
            UserProfile(u.id, u.name.ifBlank { u.email }, u.email, photoUrl)
        } catch (e: AppwriteException) {
            null
        }
    }

    fun continueAsGuest() {
        _guest.value = true
    }

    suspend fun signInWithGoogle(activity: Activity): SignInResult {
        if (!isConfigured) {
            return SignInResult.Failure("Appwrite is not configured in this build. Set the project ID in appwrite/appwrite.json (see README).")
        }
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            // GetGoogleIdOption.Builder().build() throws on a blank server client ID.
            return SignInResult.Failure("Google sign-in is not configured in this build (GOOGLE_WEB_CLIENT_ID is empty).")
        }
        return try {
            val credentialManager = CredentialManager.create(activity)
            val primary = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .build(),
                )
                .build()
            val credential = try {
                credentialManager.getCredential(activity, primary).credential
            } catch (e: NoCredentialException) {
                // The bottom-sheet flow only offers accounts Android already knows about; the
                // full "Sign in with Google" sheet lets the user pick or add any account.
                Log.w(TAG, "No credential from the primary flow, falling back to GetSignInWithGoogleOption", e)
                val fallback = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build())
                    .build()
                credentialManager.getCredential(activity, fallback).credential
            }
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(exchangeIdToken(google.idToken))
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

    private suspend fun exchangeIdToken(idToken: String): UserProfile {
        val execution = functions.createExecution(
            functionId = BuildConfig.APPWRITE_FUNCTION_MAINTENANCE_ID,
            body = JSONObject().put("idToken", idToken).toString(),
            path = "/google-sign-in",
        )
        // A bug inside the Function can leave the body empty even though createExecution
        // succeeded; surface the status instead of an opaque JSON parse error.
        if (execution.responseBody.isBlank()) {
            error("Google sign-in failed: empty response from the maintenance Function (status=${execution.status}, code=${execution.responseStatusCode}) - check its execution logs in Appwrite Console")
        }
        val body = try {
            JSONObject(execution.responseBody)
        } catch (e: JSONException) {
            error("Google sign-in failed: unexpected response from the maintenance Function")
        }
        if (!body.optBoolean("success", false)) {
            error(body.optString("message", "Google sign-in failed"))
        }
        account.createSession(userId = body.getString("userId"), secret = body.getString("secret"))
        refreshCurrentUser(photoUrl = body.optString("photoUrl", "").ifBlank { null })
        _guest.value = false
        return _user.value ?: error("Sign-in did not complete")
    }

    suspend fun signOut(activity: Activity) {
        if (isConfigured && _user.value != null) {
            runCatching { account.deleteSession(sessionId = "current") }
        }
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
        _user.value = null
        _guest.value = false
    }

    /** Deletes the caller's games and Appwrite account via the maintenance Function, then signs out. */
    suspend fun deleteAccount(activity: Activity) {
        val execution = functions.createExecution(functionId = BuildConfig.APPWRITE_FUNCTION_MAINTENANCE_ID)
        val code = execution.responseStatusCode
        if (execution.status == ExecutionStatus.FAILED || (code != 0L && code !in 200..299)) {
            error("Account deletion failed (status=${execution.status}, code=$code)")
        }
        signOut(activity)
    }

    private companion object {
        const val TAG = "AuthManager"
    }
}
