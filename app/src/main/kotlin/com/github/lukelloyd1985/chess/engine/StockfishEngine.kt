package com.github.lukelloyd1985.chess.engine

import android.content.Context
import com.github.lukelloyd1985.chess.core.BestMove
import com.github.lukelloyd1985.chess.core.Bot
import com.github.lukelloyd1985.chess.core.ChessGame
import com.github.lukelloyd1985.chess.core.EngineLine
import com.github.lukelloyd1985.chess.core.Uci
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

/** Result of one search: the final line per MultiPV slot plus the engine's chosen move. */
data class SearchResult(val lines: List<EngineLine>, val best: BestMove)

/**
 * Wraps a Stockfish 19 child process speaking UCI over stdin/stdout. The binary
 * is shipped as lib/<abi>/libstockfish.so and executed from the app's native
 * library directory. All commands are serialised by a mutex, so a single
 * instance can safely be shared by several callers.
 */
class StockfishEngine(private val context: Context) {
    private val mutex = Mutex()
    private val lines = Channel<String>(Channel.UNLIMITED)
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var started = false

    /** Starts the engine process if needed. Throws if the binary or network is unavailable. */
    suspend fun ensureStarted() = mutex.withLock { startLocked() }

    private suspend fun startLocked() {
        if (started && process?.isAlive == true) return
        withContext(Dispatchers.IO) {
            val netFile = NnueInstaller.install(context)
            val binary = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
            check(binary.exists()) { "Stockfish binary not found at ${binary.path}" }
            val p = ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            thread(name = "stockfish-reader", isDaemon = true) {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        lines.trySend(line)
                    }
                } catch (_: Exception) {
                }
                lines.trySend(EXITED)
            }
            drain()
            send("uci")
            awaitLine { it == "uciok" } ?: error("Stockfish did not answer 'uci'")
            send("setoption name EvalFile value ${netFile.absolutePath}")
            val cores = Runtime.getRuntime().availableProcessors()
            send("setoption name Threads value ${(cores / 2).coerceIn(1, 4)}")
            send("setoption name Hash value 64")
            send("isready")
            awaitLine { it == "readyok" } ?: error("Stockfish did not become ready")
            started = true
        }
    }

    private fun send(cmd: String) {
        val w = writer ?: error("Engine not running")
        w.write(cmd)
        w.write("\n")
        w.flush()
    }

    private fun drain() {
        while (lines.tryReceive().isSuccess) { /* discard stale output */ }
    }

    private suspend fun awaitLine(timeoutMs: Long = 15_000, predicate: (String) -> Boolean): String? =
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val l = lines.receive()
                if (l == EXITED) return@withTimeoutOrNull null
                if (predicate(l)) return@withTimeoutOrNull l
            }
            @Suppress("UNREACHABLE_CODE") null
        }

    /**
     * Runs a search. [goArgs] are the arguments of the UCI `go` command
     * (e.g. "movetime 500" or "depth 14"). [onInfo] receives each PV update.
     */
    suspend fun search(
        positionCommand: String,
        goArgs: String,
        multiPv: Int = 1,
        strength: Bot? = null,
        onInfo: (EngineLine) -> Unit = {},
    ): SearchResult = mutex.withLock {
        startLocked()
        withContext(Dispatchers.IO) {
            drain()
            if (strength != null) {
                if (strength.limitElo) {
                    send("setoption name UCI_LimitStrength value true")
                    send("setoption name UCI_Elo value ${Bot.uciElo(strength)}")
                    send("setoption name Skill Level value 20")
                } else {
                    send("setoption name UCI_LimitStrength value false")
                    send("setoption name Skill Level value ${strength.skill}")
                }
            } else {
                send("setoption name UCI_LimitStrength value false")
                send("setoption name Skill Level value 20")
            }
            send("setoption name MultiPV value $multiPv")
            send("isready")
            awaitLine { it == "readyok" }
            send(positionCommand)
            send("go $goArgs")

            val latest = sortedMapOf<Int, EngineLine>()
            var best: BestMove? = null
            try {
                while (best == null) {
                    val l = lines.receive()
                    if (l == EXITED) {
                        started = false
                        error("Stockfish process exited")
                    }
                    Uci.parseBestMove(l)?.let { best = it } ?: Uci.parseInfo(l)?.let {
                        latest[it.multiPv] = it
                        onInfo(it)
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    runCatching { send("stop") }
                    awaitLine(5_000) { it.startsWith("bestmove") }
                }
                throw e
            }
            SearchResult(latest.values.toList(), best!!)
        }
    }

    /** Picks a move for [bot] in the current position of [game], or null if none is available. */
    suspend fun botMove(game: ChessGame, bot: Bot): String? {
        val multi = if (bot.sloppiness > 0) 5 else 1
        val go = if (bot.depth != null) "depth ${bot.depth} movetime ${bot.moveTimeMs}" else "movetime ${bot.moveTimeMs}"
        val result = search(game.uciPositionCommand(), go, multi, bot)
        val best = result.best.move ?: return null
        val candidates = buildList {
            add(best)
            result.lines.mapNotNull { it.pv.firstOrNull() }.forEach { if (it !in this) add(it) }
        }
        return Bot.pickMove(bot, candidates)
    }

    fun close() {
        runCatching { writer?.let { it.write("quit\n"); it.flush() } }
        runCatching { process?.destroy() }
        process = null
        writer = null
        started = false
    }

    private companion object {
        const val EXITED = "\u0000EXITED"
    }
}

/** Copies the bundled NNUE network from assets to app storage (once) so Stockfish can read it by path. */
object NnueInstaller {
    private const val ASSET_NAME = "nn-1a298aa575a0.nnue"

    fun install(context: Context): File {
        val dir = File(context.filesDir, "nnue").apply { mkdirs() }
        val target = File(dir, ASSET_NAME)
        val assetSize = context.assets.openFd(ASSET_NAME).use { it.length }
        if (target.exists() && target.length() == assetSize) return target
        val tmp = File(dir, "$ASSET_NAME.tmp")
        context.assets.open(ASSET_NAME).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.renameTo(target)) { "Could not install NNUE network" }
        return target
    }
}
