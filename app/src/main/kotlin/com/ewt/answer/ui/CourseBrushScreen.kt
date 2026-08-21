package com.ewt.answer.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import com.ewt.answer.data.AppContainer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * 课程页：Python 刷课（Chaquopy 内嵌 ewt_brush_v2，miuix 原生排版）。
 * 自动读取主 App 已登录 token（无需账号密码）；日志摘要显示，可切换 Detail；参数在二级设置页。
 */
@Composable
fun CourseBrushScreen(
    settings: BrushSettings,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val logFile = remember { File(context.filesDir, "brush.log") }
    val listState = rememberLazyListState()

    val hwState = rememberTextFieldState()
    val accountState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()

    var logText by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var dryRun by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }

    // 自动获取主 App 已登录 token
    val savedToken = remember { AppContainer.tokenStore.load() }

    // 日志轮询：增量读取 brush.log
    val handler = remember { Handler(Looper.getMainLooper()) }
    var lastLen by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) {
        val poller = object : Runnable {
            override fun run() {
                try {
                    if (logFile.exists()) {
                        val len = logFile.length()
                        if (len > lastLen) {
                            val n = (len - lastLen).coerceAtMost(64 * 1024L)
                            RandomAccessFile(logFile, "r").use { raf ->
                                raf.seek(lastLen)
                                val buf = ByteArray(n.toInt())
                                raf.readFully(buf)
                                lastLen += n
                                logText += String(buf, Charsets.UTF_8)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                handler.postDelayed(this, 400)
            }
        }
        handler.postDelayed(poller, 400)
        onDispose { handler.removeCallbacks(poller) }
    }

    // 日志自动滑到底部
    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) listState.scrollToItem(count - 1)
        }
    }

    fun runBrush() {
        if (running) return
        val hw = hwState.text.toString().trim()
        val account = accountState.text.toString().trim()
        val password = passwordState.text.toString()
        val token = savedToken

        running = true
        thread {
            try {
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = logFile.absolutePath
                val ret: Int = if (!token.isNullOrBlank()) {
                    // 有已登录 token：直接刷课（token 失效时用可选账号密码续期）
                    mod.callAttr(
                        "run_brush_token", logPath, token, account, password, hw,
                        settings.concurrency, settings.qps, dryRun, settings.burst,
                    ).toInt()
                } else {
                    // 无 token：退回账号密码登录刷课
                    mod.callAttr(
                        "run_brush", logPath, account, password, hw,
                        settings.concurrency, settings.qps, dryRun, settings.burst,
                    ).toInt()
                }
                handler.post { logText += "\n==== 结束，返回码 $ret ====\n" }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
            }
        }
    }

    // ── 摘要算法：过滤噪音行，保留关键进度 ──
    // 噪音：httpx access 日志（"2026-08-21 20:45:15,708 INFO HTTP Request: ..."）、
    //       [BFE-REQ] 请求详情（含 body/headers 大段）、空行
    // 保留：[进度] 轮次、▶ 课时开始、[完成]/[通过]、✓/⚠/✗ 状态、共发现/总时长、处理完成等
    val summaryText = remember(logText) {
        logText.lineSequence()
            .map { it.trimEnd() }
            .filter { line ->
                val t = line.trim()
                t.isNotEmpty() &&
                    !t.startsWith("[BFE-REQ]") &&
                    !t.startsWith("  [BFE-REQ]") &&
                    !t.contains("INFO HTTP Request:")
            }
            .joinToString("\n")
            .ifBlank { "等待开始…" }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 大标题行 + 设置按钮（齿轮，与底栏同款）
            item(key = "large_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 0.dp, top = 4.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "课程",
                        fontSize = MiuixTheme.textStyles.title1.fontSize,
                        fontWeight = FontWeight.Normal,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // 设置（齿轮）→ 二级设置页
                    IconButton(onClick = onOpenSettings) {
                        Image(
                            painter = rememberVectorPainter(SettingsTabIcon),
                            contentDescription = "刷课设置",
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                        )
                    }
                }
            }
            // 登录态提示
            item(key = "token_status") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (savedToken.isNullOrBlank()) "未检测到登录 token，请先登录" else "已登录：${savedToken.take(12)}…${savedToken.takeLast(6)}",
                            fontSize = 12.sp,
                            color = if (savedToken.isNullOrBlank()) {
                                MiuixTheme.colorScheme.error
                            } else {
                                MiuixTheme.colorScheme.primary
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = dryRun,
                            onCheckedChange = { dryRun = it },
                        )
                        Text(
                            text = "仅扫描",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            // 参数摘要卡（只读展示，点击进设置页）
            item(key = "params_summary") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    onClick = onOpenSettings,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "并行 ${settings.concurrency} · QPS ${settings.qps} · 爆发 ${settings.burst}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "设置 ›",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
            // 账号密码（可选，仅 token 失效续期用）
            item(key = "account") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            state = accountState,
                            label = "账号（可选，token 失效时续期用）",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            state = passwordState,
                            label = "密码",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            state = hwState,
                            label = "只刷指定作业 ID（可选）",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { runBrush() },
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (running) "刷课中…" else "开始刷课", fontSize = 14.sp)
                    }
                }
            }
            item(key = "log_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallTitle(text = "运行日志")
                    Spacer(Modifier.weight(1f))
                    // 摘要 / 详细切换
                    TextButton(
                        text = if (showDetail) "Summary" else "Detail",
                        onClick = { showDetail = !showDetail },
                    )
                    // 清空
                    IconButton(
                        onClick = {
                            logFile.delete()
                            logText = ""
                            lastLen = 0
                        },
                    ) {
                        Text("清空", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
            item(key = "log") {
                // 日志框：背景 + 边框
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (showDetail) logText.ifEmpty { "等待开始…" } else summaryText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = if (logText.isEmpty()) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
