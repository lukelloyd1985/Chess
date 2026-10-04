package com.github.lukelloyd1985.chess.data

import android.content.Context
import com.github.lukelloyd1985.chess.core.Pgn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A finished or imported game kept on the device. */
data class SavedGame(
    val id: String,
    val pgn: String,
    val white: String,
    val black: String,
    val result: String,
    val mode: String,
    val savedAt: Long,
) {
    val title: String get() = "$white vs $black"
}

/**
 * Local game history, stored as JSON in app-private storage. There is no
 * limit on how many games are kept or analysed.
 */
class GameStore(context: Context) {
    private val file = File(context.filesDir, "games.json")
    private val lock = Any()

    fun all(): List<SavedGame> = synchronized(lock) { read().sortedByDescending { it.savedAt } }

    fun get(id: String): SavedGame? = synchronized(lock) { read().firstOrNull { it.id == id } }

    fun save(game: SavedGame) = synchronized(lock) {
        val list = read().filter { it.id != game.id } + game
        write(list)
    }

    fun delete(id: String) = synchronized(lock) { write(read().filter { it.id != id }) }

    /** Parses [pgn] (throws [IllegalArgumentException] if invalid) and stores it as an imported game. */
    fun importPgn(pgn: String): SavedGame {
        val parsed = Pgn.parse(pgn)
        val saved = SavedGame(
            id = java.util.UUID.randomUUID().toString(),
            pgn = pgn.trim(),
            white = parsed.headers["White"] ?: "White",
            black = parsed.headers["Black"] ?: "Black",
            result = parsed.resultToken ?: "*",
            mode = "import",
            savedAt = System.currentTimeMillis(),
        )
        save(saved)
        return saved
    }

    private fun read(): List<SavedGame> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SavedGame(
                    o.getString("id"), o.getString("pgn"), o.optString("white", "White"), o.optString("black", "Black"),
                    o.optString("result", "*"), o.optString("mode", ""), o.optLong("savedAt"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<SavedGame>) {
        val arr = JSONArray()
        for (g in list) {
            arr.put(
                JSONObject().put("id", g.id).put("pgn", g.pgn).put("white", g.white).put("black", g.black)
                    .put("result", g.result).put("mode", g.mode).put("savedAt", g.savedAt),
            )
        }
        val tmp = File(file.parentFile, "games.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
