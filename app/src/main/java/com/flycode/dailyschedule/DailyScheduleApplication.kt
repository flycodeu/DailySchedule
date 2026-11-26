package com.flycode.dailyschedule

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class DailyScheduleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildString {
                appendLine("App: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Device: ${Build.BRAND} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.name}")
                appendLine()
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
            }
            val opened = runCatching {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_REPORT, report)
                }
                startActivity(intent)
            }.isSuccess

            if (opened) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}

class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT) ?: "未知异常"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(42, 96, 42, 48)
        }

        val title = TextView(this).apply {
            text = "日常计划启动遇到异常"
            textSize = 20f
            setTextColor(Color.parseColor("#18201E"))
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "可点击下方按钮复制完整报错日志用于快速修复："
            textSize = 14f
            setTextColor(Color.parseColor("#54625D"))
            setPadding(0, 12, 0, 20)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        val copyBtn = Button(this).apply {
            text = "复制报错日志"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("DailyScheduleCrash", report))
                Toast.makeText(this@CrashActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
        val restartBtn = Button(this).apply {
            text = "重试打开"
            setOnClickListener {
                startActivity(
                    Intent(this@CrashActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
        buttons.addView(copyBtn)
        buttons.addView(restartBtn)

        val logView = TextView(this).apply {
            text = report
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#B91C1C"))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(buttons)
        root.addView(scroll)
        setContentView(root)
    }

    companion object {
        const val EXTRA_REPORT = "extra_crash_report"
    }
}
