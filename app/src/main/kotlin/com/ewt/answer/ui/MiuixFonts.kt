package com.ewt.answer.ui

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MiSans 字体动态加载器。
 *
 * 字体文件不打包进 APK，而是托管在 GitHub 仓库 fonts/ 目录：
 *   https://raw.githubusercontent.com/jj-m-j/ewttest/main/fonts/MiSansVF.ttf
 *
 * - 由用户确认后下载 MiSansVF 可变字体（约 20MB）到缓存目录
 * - 之后启动直接使用本地缓存，离线可用
 * - 开关关闭时恢复系统字体并删除缓存
 * - 下载/加载失败时返回 null，调用方回退系统字体
 */
object MiuixFonts {

    private const val FONT_BASE = "https://raw.githubusercontent.com/jj-m-j/ewttest/main/fonts/"
    /** 可变字体文件名（仓库中） */
    private const val REMOTE_VF_NAME = "MiSansVF.ttf"
    /** 本地缓存文件名 */
    private const val CACHE_VF_NAME = "misans_vf.ttf"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 全部字重映射（可变字体通过 wght variation 动态匹配） */
    private val weights = listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )

    /** 字体是否已下载到缓存 */
    fun isDownloaded(context: Context): Boolean {
        val file = File(File(context.filesDir, "fonts"), CACHE_VF_NAME)
        return file.exists() && file.length() > 0L
    }

    /** 已下载字体大小（MB 字符串），未下载返回空 */
    fun downloadedMb(context: Context): String {
        val file = File(File(context.filesDir, "fonts"), CACHE_VF_NAME)
        if (!file.exists()) return ""
        return String.format("%.1f MB", file.length() / 1024.0 / 1024.0)
    }

    /**
     * 加载 MiSans 字体族（自动下载 + 缓存）。
     * 返回 null 表示加载失败，调用方应回退系统默认字体。
     */
    suspend fun loadMiSans(context: Context): FontFamily? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val file = File(dir, CACHE_VF_NAME)
        if (!file.exists() || file.length() == 0L) {
            runCatching { download(FONT_BASE + REMOTE_VF_NAME, file) }
        }
        if (file.exists() && file.length() > 0L) {
            // 同一可变字体文件按不同字重注册，Compose 请求对应 wght（API 26+）
            FontFamily(weights.map { Font(file, it) })
        } else {
            null
        }
    }

    /** 删除缓存（开关关闭时调用，恢复系统字体） */
    fun clearCache(context: Context) {
        File(context.filesDir, "fonts").deleteRecursively()
    }

    private fun download(url: String, target: File) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) {
                resp.body?.byteStream()?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
