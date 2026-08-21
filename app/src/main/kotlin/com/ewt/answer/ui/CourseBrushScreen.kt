package com.ewt.answer.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

/** 设置图标（齿轮，material settings 简版） */
private val SettingsGlyph = "\u2699\uFE0F"

/**
 * 课程页：Python 刷课（Chaquopy 内嵌 ewt_brush_v2，miuix 原生排版）。
 * 自动使用主 App 已登录 token；日志默认显示摘要，可切换详细；参数走设置气泡。
 */
@Composable
fun CourseBrushScreen() {
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
    var showSettings by remember { mutableStateOf(false) }
    var concurrency by remember { mutableStateOf("6") }
    var qps by remember { mutableStateOf("150") }
    var burst by remember { mutableStateOf("8") }

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

    fun runPy(fn: String) {
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
                val ret: Int = if (fn == "run_brush" && !token.isNullOrBlank()) {
                    // 有已登录 token：直接刷课，无需重新登录
                    mod.callAttr("run_brush_token", logPath, token, account, password, hw, concurrency, qps, dryRun, burst).toInt()
                } else {
                    // 无 token 或仅登录：走账号密码
                    if (fn == "do_login") {
                        mod.callAttr("do_login", logPath, account, password).toInt()
                    } else {
                        mod.callAttr("run_brush", logPath, account, password, hw, concurrency, qps, dryRun, burst).toInt()
                    }
                }
                handler.post { logText += "\n==== 结束，返回码 $ret ====\n" }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
            }
        }
    }

    // 摘要：过滤噪音行（BFE 请求详情 / 逐轮进度）
    val summaryText = remember(logText) {
        logText.lineSequence()
            .filter { line ->
                val t = line.trim()
                t.isNotEmpty() &&
                    !t.startsWith("[BFE-REQ]") &&
                    !t.startsWith("[进度]") &&
                    !t.startsWith("  [进度]") &&
                    t != "等待开始…"
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
            // 大标题行 + 设置按钮（miuix 原生排版）
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
                    // 设置按钮：并行路数 / QPS / 爆发 气泡设置
                    Box {
                        IconButton(onClick = { showSettings = true }) {
                            Text(
                                text = SettingsGlyph,
                                fontSize = 20.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        if (showSettings) {
                            BrushSettingsPopup(
                                onDismiss = { showSettings = false },
                                concurrency = concurrency,
                                qps = qps,
                                burst = burst,
                                onConcurrency = { concurrency = it },
                                onQps = { qps = it },
                                onBurst = { burst = it },
                            )
                        }
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
                            text = if (savedToken.isNullOrBlank()) "未检测到登录 token" else "已登录：${savedToken.take(12)}…${savedToken.takeLast(6)}",
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
                            label = "账号（token 失效时自动续期用，可空）",
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
                        onClick = { runPy("do_login") },
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (running) "运行中…" else "登录", fontSize = 14.sp)
                    }
                    Button(
                        onClick = { runPy("run_brush") },
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
                        text = if (showDetail) "显示摘要" else "显示详细",
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

/** 设置气泡：并行路数 / QPS / 爆发 可选值平铺（参照三条杠弹层） */
@Composable
private fun BrushSettingsPopup(
    onDismiss: () -> Unit,
    concurrency: String,
    qps: String,
    burst: String,
    onConcurrency: (String) -> Unit,
    onQps: (String) -> Unit,
    onBurst: (String) -> Unit,
) {
    val morph = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        morph.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    val p = morph.value
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = Modifier
                .width(260.dp)
                .graphicsLayer { alpha = p },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ParamRow(
                    label = "并行路数",
                    options = listOf("1", "2", "4", "6", "8", "12"),
                    selected = concurrency,
                    onSelect = onConcurrency,
                )
                ParamRow(
                    label = "QPS",
                    options = listOf("50", "100", "150", "200", "300", "400"),
                    selected = qps,
                    onSelect = onQps,
                )
                ParamRow(
                    label = "爆发",
                    options = listOf("4", "6", "8", "12", "16"),
                    selected = burst,
                    onSelect = onBurst,
                )
            }
        }
    }
}

/** 参数行：标签 + 可选项平铺（选中高亮） */
@Composable
private fun ParamRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { opt ->
                val isSel = opt == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSel) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent,
                        ),
                ) {
                    TextButton(
                        text = opt,
                        onClick = { onSelect(opt) },
                    )
                }
            }
        }
    }
}
