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
 * - 追加写入 filesDir/logs/app.log
 * - 超过 512KB 自动轮转为 app.log.old
 * - 提供读取 / 清空，UI 中可查看、分享、复制
 */
object DebugLog {

    private const val TAG = "EWT"
    private const val MAX_SIZE = 512 * 1024L

    private var file: File? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        file = File(dir, "app.log")
    }

    fun d(tag: String, msg: String) {
        Log.d("$TAG-$tag", msg)
        write("[${ts()}] [D/$tag] $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e("$TAG-$tag", msg, t)
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
