package com.github.lukelloyd1985.chess.crash

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import com.github.lukelloyd1985.chess.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Catches any uncaught exception, saves a report and opens [CrashActivity] (in its own process,
 * so it still works when the app process or its initialisation is what broke) instead of the
 * app just vanishing.
 */
object CrashHandler {
    const val EXTRA_REPORT = "report"
    private const val FILE_NAME = "last_crash.txt"

    // Intent extras travel over Binder (~1 MB limit), so keep the report well below that.
    private const val MAX_REPORT_CHARS = 60_000

    /** True when running inside the dedicated `:crash` process that hosts [CrashActivity]. */
    fun isCrashProcess(): Boolean = try {
        File("/proc/self/cmdline").readText().trim { it == '\u0000' || it.isWhitespace() }.endsWith(":crash")
    } catch (e: Exception) {
        false
    }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never recurse if the crash screen itself crashes: fall back to the system handler.
            if (isCrashProcess()) {
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            try {
                val report = buildReport(thread, throwable).take(MAX_REPORT_CHARS)
                runCatching { File(app.filesDir, FILE_NAME).writeText(report) }
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(EXTRA_REPORT, report),
                )
            } catch (t: Throwable) {
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("Chess crashed")
            appendLine()
            appendLine("Time:     $time")
            appendLine("Thread:   ${thread.name}")
            appendLine("App:      ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("Device:   ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Backend:  ${BuildConfig.APPWRITE_ENDPOINT}, project ${BuildConfig.APPWRITE_PROJECT_ID.ifBlank { "(none)" }}")
            appendLine()
            appendLine("${throwable::class.java.name}: ${throwable.message}")
            appendLine()
            append(trace)
        }
    }
}
