package com.github.lukelloyd1985.chess.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.github.lukelloyd1985.chess.MainActivity
import java.io.File

/**
 * Shows the error behind a crash. Deliberately built from plain Views (no Compose, no app
 * singletons) and run in its own process so it cannot be taken down by whatever crashed the app.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(CrashHandler.EXTRA_REPORT)
            ?: runCatching { File(filesDir, "last_crash.txt").readText() }.getOrNull()
            ?: "No crash report was available."

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val title = TextView(this).apply {
            text = "Something went wrong"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "The app hit an unexpected error. Copy or share the details below so it can be fixed."
            textSize = 14f
            setTextColor(Color.parseColor("#A09D98"))
            setPadding(0, px(4), 0, px(12))
        }
        val body = TextView(this).apply {
            text = report
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.parseColor("#F1F1F1"))
            setTextIsSelectable(true)
            setPadding(px(10), px(10), px(10), px(10))
            setBackgroundColor(Color.parseColor("#1B1A18"))
        }
        val scroll = ScrollView(this).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, px(12), 0, 0)
            addView(button("Copy") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Chess crash report", report))
                Toast.makeText(this@CrashActivity, "Copied", Toast.LENGTH_SHORT).show()
            })
            addView(button("Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Chess crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(send, "Share crash report"))
            })
            addView(button("Restart") {
                startActivity(
                    Intent(this@CrashActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                finish()
            })
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#262421"))
                setPadding(px(16), px(16), px(16), px(16))
                // Keeps content clear of the system bars under forced edge-to-edge.
                fitsSystemWindows = true
                addView(title)
                addView(subtitle)
                addView(scroll)
                addView(buttons)
            },
        )
    }
}
