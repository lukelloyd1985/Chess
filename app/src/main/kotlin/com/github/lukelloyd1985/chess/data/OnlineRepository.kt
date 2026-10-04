package com.github.lukelloyd1985.chess.data

import android.content.Context
import com.github.lukelloyd1985.chess.auth.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** A friend game stored in Firestore under games/{code}. */
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
 * Friend games over Firestore: one player creates a game and shares its
 * 6-character code, the other joins with it. Moves are appended to a shared
 * list with transactions so both devices stay in sync.
 */
class OnlineRepository(private val context: Context) {
    private val available: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val games get() = db.collection("games")

    private fun requireAvailable() {
        check(available) { "Online play needs Firebase. Add app/google-services.json (see README) and rebuild." }
    }

    /** Creates a waiting game and returns its share code. [asWhite] null means random. */
    suspend fun createGame(user: UserProfile, asWhite: Boolean?): String {
        requireAvailable()
        val white = asWhite ?: (System.nanoTime() % 2 == 0L)
        repeat(8) {
            val code = newCode()
            val ref = games.document(code)
            val created = db.runTransaction { tx ->
                if (tx.get(ref).exists()) return@runTransaction false
                tx.set(
                    ref,
                    hashMapOf(
                        "whiteUid" to if (white) user.uid else null,
                        "blackUid" to if (white) null else user.uid,
                        "whiteName" to if (white) user.name else "Waiting…",
                        "blackName" to if (white) "Waiting…" else user.name,
                        "players" to listOf(user.uid),
                        "moves" to emptyList<String>(),
                        "status" to "waiting",
                        "result" to null,
                        "reason" to null,
                        "drawOfferBy" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                true
            }.await()
            if (created) return code
        }
        error("Could not allocate a game code, please try again")
    }

    /** Joins the open seat of game [rawCode]; rejoining your own game is allowed. */
    suspend fun joinGame(user: UserProfile, rawCode: String): String {
        requireAvailable()
        val code = rawCode.trim().uppercase()
        val ref = games.document(code)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            check(snap.exists()) { "No game with code $code" }
            val white = snap.getString("whiteUid")
            val black = snap.getString("blackUid")
            if (user.uid == white || user.uid == black) return@runTransaction Unit
            check(white == null || black == null) { "That game already has two players" }
            val joinsAsWhite = white == null
            val update = hashMapOf<String, Any?>(
                "players" to FieldValue.arrayUnion(user.uid),
                "status" to "active",
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (joinsAsWhite) {
                update["whiteUid"] = user.uid
                update["whiteName"] = user.name
            } else {
                update["blackUid"] = user.uid
                update["blackName"] = user.name
            }
            tx.update(ref, update)
            Unit
        }.await()
        return code
    }

    fun observe(code: String): Flow<OnlineGame?> = callbackFlow {
        requireAvailable()
        val reg = games.document(code).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.let { parse(code, it.data.orEmpty()) })
        }
        awaitClose { reg.remove() }
    }

    fun observeMyGames(uid: String): Flow<List<OnlineGame>> = callbackFlow {
        requireAvailable()
        val reg = games.whereArrayContains("players", uid)
            .limit(30)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { parse(it.id, it.data.orEmpty()) }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    /** Appends [uci] if the shared move list still has exactly [expectedPly] moves and it is [uid]'s turn. */
    suspend fun submitMove(code: String, uid: String, uci: String, expectedPly: Int) {
        requireAvailable()
        val ref = games.document(code)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val g = parse(code, snap.data.orEmpty())
            check(g.status == "active") { "Game is not active" }
            check(g.moves.size == expectedPly) { "Out of sync, the position changed" }
            val whiteTurn = g.moves.size % 2 == 0
            check(g.colorOf(uid) == whiteTurn) { "Not your turn" }
            tx.update(
                ref,
                mapOf(
                    "moves" to g.moves + uci,
                    "drawOfferBy" to null,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            Unit
        }.await()
    }

    suspend fun finish(code: String, result: String, reason: String) {
        requireAvailable()
        val ref = games.document(code)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") == "finished") return@runTransaction Unit
            tx.update(
                ref,
                mapOf(
                    "status" to "finished",
                    "result" to result,
                    "reason" to reason,
                    "drawOfferBy" to null,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            Unit
        }.await()
    }

    suspend fun offerDraw(code: String, uid: String) {
        requireAvailable()
        games.document(code).update(mapOf("drawOfferBy" to uid, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun declineDraw(code: String) {
        requireAvailable()
        games.document(code).update(mapOf("drawOfferBy" to null, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(code: String, d: Map<String, Any?>): OnlineGame = OnlineGame(
        code = code,
        whiteUid = d["whiteUid"] as? String,
        blackUid = d["blackUid"] as? String,
        whiteName = d["whiteName"] as? String ?: "White",
        blackName = d["blackName"] as? String ?: "Black",
        moves = (d["moves"] as? List<String>).orEmpty(),
        status = d["status"] as? String ?: "waiting",
        result = d["result"] as? String,
        reason = d["reason"] as? String,
        drawOfferBy = d["drawOfferBy"] as? String,
    )

    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous 0/O/1/I
        return (1..6).map { alphabet[java.security.SecureRandom().nextInt(alphabet.length)] }.joinToString("")
    }
}
