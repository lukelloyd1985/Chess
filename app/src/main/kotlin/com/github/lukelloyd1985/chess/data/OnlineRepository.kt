package com.github.lukelloyd1985.chess.data

import com.github.lukelloyd1985.chess.BuildConfig
import com.github.lukelloyd1985.chess.auth.UserProfile
import io.appwrite.Channel
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Document
import io.appwrite.services.Databases
import io.appwrite.services.Functions
import io.appwrite.services.Realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/** A friend game stored in the Appwrite `games` table; the row ID is the 6-character share code. */
data class OnlineGame(
    val code: String,
    val whiteUid: String?,
    val blackUid: String?,
    val whiteName: String,
    val blackName: String,
    val moves: List<String>,
    /** "waiting" until the second player joins, then "active", then "finished". */
    val status: String,
    /** PGN result token ("1-0", "0-1", "1/2-1/2") once finished. */
    val result: String?,
    val reason: String?,
    /** Uid of the player currently offering a draw, if any. */
    val drawOfferBy: String?,
) {
    fun colorOf(uid: String): Boolean? = when (uid) {
        whiteUid -> true
        blackUid -> false
        else -> null
    }
}

/**
 * Friend games over Appwrite: one player creates a game row and shares its code, the other joins
 * through the `maintenance` Function (which seats them and widens the row's permissions). Moves
 * are appended to the shared row and both devices follow it through Realtime.
 */
class OnlineRepository(
    private val databases: Databases,
    private val realtime: Realtime,
    private val functions: Functions,
) {
    private val databaseId = BuildConfig.APPWRITE_DATABASE_ID
    private val gamesId = BuildConfig.APPWRITE_COLLECTION_GAMES_ID

    /** Creates a waiting game and returns its share code. [asWhite] null means random. */
    suspend fun createGame(user: UserProfile, asWhite: Boolean?): String {
        val white = asWhite ?: (System.nanoTime() % 2 == 0L)
        repeat(8) {
            val code = newCode()
            try {
                databases.createDocument(
                    databaseId = databaseId,
                    collectionId = gamesId,
                    documentId = code,
                    data = mapOf(
                        "whiteUid" to if (white) user.uid else "",
                        "blackUid" to if (white) "" else user.uid,
                        "whiteName" to if (white) user.name else "Waiting…",
                        "blackName" to if (white) "Waiting…" else user.name,
                        "moves" to "",
                        "status" to "waiting",
                        "drawOfferBy" to "",
                    ),
                    permissions = listOf(
                        Permission.read(Role.user(user.uid)),
                        Permission.update(Role.user(user.uid)),
                        Permission.delete(Role.user(user.uid)),
                    ),
                )
                return code
            } catch (e: AppwriteException) {
                if (e.code != 409) throw e // 409 = code already taken: try another
            }
        }
        error("Could not allocate a game code, please try again")
    }

    /** Joins the open seat of game [rawCode] (rejoining your own game is allowed); returns its code. */
    suspend fun joinGame(rawCode: String): String {
        val code = rawCode.trim().uppercase()
        val execution = functions.createExecution(
            functionId = BuildConfig.APPWRITE_FUNCTION_MAINTENANCE_ID,
            body = JSONObject().put("code", code).toString(),
            path = "/join-game",
        )
        if (execution.responseBody.isBlank()) {
            error("Could not join: empty response from the server (status=${execution.status})")
        }
        val body = try {
            JSONObject(execution.responseBody)
        } catch (e: JSONException) {
            error("Could not join: unexpected response from the server")
        }
        if (!body.optBoolean("success", false)) error(body.optString("message", "Could not join that game"))
        return code
    }

    fun observe(code: String): Flow<OnlineGame?> = callbackFlow {
        suspend fun refresh() {
            try {
                trySend(databases.getDocument(databaseId, gamesId, code).toGame())
            } catch (e: AppwriteException) {
                if (e.code == 404 || e.code == 401) trySend(null) else close(e)
            } catch (t: Throwable) {
                close(t)
            }
        }
        refresh()
        // Realtime subscribes at table granularity; filter to this game before refetching.
        val subscription = realtime.subscribe(Channel.tablesdb(databaseId).table(gamesId).row()) { response ->
            @Suppress("UNCHECKED_CAST")
            val payload = response.payload as? Map<String, Any?>
            val id = payload?.get("\$id") as? String
            if (id == null || id == code) launch { refresh() }
        }
        awaitClose { subscription.close() }
    }

    fun observeMyGames(): Flow<List<OnlineGame>> = callbackFlow {
        suspend fun refresh() {
            try {
                // Row-level permissions mean this only returns games the signed-in user is seated in.
                val result = databases.listDocuments(
                    databaseId,
                    gamesId,
                    queries = listOf(Query.orderDesc("\$updatedAt"), Query.limit(30)),
                )
                trySend(result.documents.map { it.toGame() })
            } catch (t: Throwable) {
                close(t)
            }
        }
        refresh()
        val subscription = realtime.subscribe(Channel.tablesdb(databaseId).table(gamesId).row()) { launch { refresh() } }
        awaitClose { subscription.close() }
    }

    /** Appends [uci] if the shared move list still has exactly [expectedPly] moves and it is [uid]'s turn. */
    suspend fun submitMove(code: String, uid: String, uci: String, expectedPly: Int) {
        val g = databases.getDocument(databaseId, gamesId, code).toGame()
        check(g.status == "active") { "Game is not active" }
        check(g.moves.size == expectedPly) { "Out of sync, the position changed" }
        val whiteTurn = g.moves.size % 2 == 0
        check(g.colorOf(uid) == whiteTurn) { "Not your turn" }
        databases.updateDocument(
            databaseId, gamesId, code,
            mapOf("moves" to (g.moves + uci).joinToString(" "), "drawOfferBy" to ""),
        )
    }

    suspend fun finish(code: String, result: String, reason: String) {
        val g = databases.getDocument(databaseId, gamesId, code).toGame()
        if (g.status == "finished") return
        databases.updateDocument(
            databaseId, gamesId, code,
            mapOf("status" to "finished", "result" to result, "reason" to reason, "drawOfferBy" to ""),
        )
    }

    suspend fun offerDraw(code: String, uid: String) {
        databases.updateDocument(databaseId, gamesId, code, mapOf("drawOfferBy" to uid))
    }

    suspend fun declineDraw(code: String) {
        databases.updateDocument(databaseId, gamesId, code, mapOf("drawOfferBy" to ""))
    }

    private fun Document<Map<String, Any>>.toGame(): OnlineGame {
        val f = data
        fun str(key: String): String? = (f[key] as? String)?.takeIf { it.isNotEmpty() }
        return OnlineGame(
            code = id,
            whiteUid = str("whiteUid"),
            blackUid = str("blackUid"),
            whiteName = str("whiteName") ?: "White",
            blackName = str("blackName") ?: "Black",
            moves = str("moves")?.split(' ')?.filter { it.isNotEmpty() }.orEmpty(),
            status = str("status") ?: "waiting",
            result = str("result"),
            reason = str("reason"),
            drawOfferBy = str("drawOfferBy"),
        )
    }

    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous 0/O/1/I
        val rng = java.security.SecureRandom()
        return (1..6).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
    }
}
