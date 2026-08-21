package com.ewt.answer.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.chaquo.python.Python
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
 * 账号/密码 → 登录/刷课，日志实时轮询显示。
 */
@Composable
fun CourseBrushScreen() {
    val context = LocalContext.current
    val logFile = remember { File(context.filesDir, "brush.log") }
    val listState = rememberLazyListState()

    val accountState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()
    val hwState = rememberTextFieldState()
    val concurrencyState = rememberTextFieldState()
    val qpsState = rememberTextFieldState()
    val burstState = rememberTextFieldState()

    var logText by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var dryRun by remember { mutableStateOf(false) }

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

    // 滚动到底部
    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    fun runPy(fn: String) {
        if (running) return
        val account = accountState.text.toString().trim()
        val password = passwordState.text.toString()
        if (account.isEmpty() || password.isEmpty()) return
        val concurrency = concurrencyState.text.toString().trim().ifEmpty { "6" }
        val qps = qpsState.text.toString().trim().ifEmpty { "150" }
        val burst = burstState.text.toString().trim().ifEmpty { "8" }
        val hw = hwState.text.toString().trim()

        running = true
        thread {
            try {
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = logFile.absolutePath
                val ret: Int = if (fn == "do_login") {
                    mod.callAttr("do_login", logPath, account, password).toInt()
                } else {
                    mod.callAttr("run_brush", logPath, account, password, hw, concurrency, qps, dryRun, burst).toInt()
                }
                handler.post { logText += "\n==== 结束，返回码 $ret ====\n" }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
            }
        }
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
            // 大标题行（miuix 原生排版：左右 26dp 边距、下方留白）
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
                }
            }
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
                            label = "账号",
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
            item(key = "params") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextField(
                                state = concurrencyState,
                                label = "并行路数",
                                useLabelAsPlaceholder = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextField(
                                state = qpsState,
                                label = "QPS",
                                useLabelAsPlaceholder = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextField(
                                state = burstState,
                                label = "爆发",
                                useLabelAsPlaceholder = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "仅扫描（dry-run）",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = dryRun,
                                onCheckedChange = { dryRun = it },
                            )
                        }
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
                    TextButton(
                        text = if (running) "运行中…" else "仅登录",
                        onClick = { runPy("do_login") },
                        enabled = !running,
                    )
                    Button(
                        onClick = { runPy("run_brush") },
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (running) "刷课中…" else "开始刷课", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = {
                            logFile.delete()
                            logText = ""
                            lastLen = 0
                        },
                    ) {
                        Text("清空", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
            item(key = "log_title") {
                SmallTitle(text = "运行日志")
            }
            item(key = "log") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = logText.ifEmpty { "等待开始…" },
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
