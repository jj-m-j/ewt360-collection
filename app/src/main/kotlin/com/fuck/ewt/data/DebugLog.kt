package com.fuck.ewt.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地文件日志（调试模式）。
 *
 * - 默认关闭：只有开启「记录日志」开关才写文件（否则仅 Logcat，不写盘）。
 * - 每次启动 App 自动清空日志文件。
 * - 超过 512KB 自动轮转为 app.log.old。
 */
object DebugLog {

    private const val TAG = "EWT"
    private const val MAX_SIZE = 512 * 1024L
    private const val PREFS = "ewt"
    private const val KEY_ENABLED = "debug_log"

    @Volatile
    var enabled: Boolean = false
        private set

    private var file: File? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        file = File(dir, "app.log")
        // 每次启动清空
        runCatching { file?.delete() }
        runCatching { file?.createNewFile() }
        // 读取开关（首次默认 false）
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    }

    /** 切换开关，写回持久化（默认 false） */
    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun d(tag: String, msg: String) {
        Log.d("$TAG-$tag", msg)
        if (enabled) write("[${ts()}] [D/$tag] $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e("$TAG-$tag", msg, t)
        if (!enabled) return
        val stack = t?.let { "\n" + it.stackTraceToString() } ?: ""
        write("[${ts()}] [E/$tag] $msg$stack")
    }

    /** 读取全部日志 */
    fun readLog(): String = synchronized(this) {
        file?.takeIf { it.exists() }?.readText() ?: "(暂无日志)"
    }

    /** 清空日志 */
    fun clear() {
        synchronized(this) {
            file?.let {
                runCatching { it.delete() }
                runCatching { it.createNewFile() }
            }
        }
    }

    private fun write(line: String) {
        synchronized(this) {
            val f = file ?: return
            try {
                if (f.exists() && f.length() > MAX_SIZE) {
                    val old = File(f.parentFile, "app.log.old")
                    runCatching { f.renameTo(old) }
                }
                f.appendText(line + "\n")
            } catch (e: Exception) {
                // 日志失败不影响主流程
            }
        }
    }

    private fun ts(): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
}
