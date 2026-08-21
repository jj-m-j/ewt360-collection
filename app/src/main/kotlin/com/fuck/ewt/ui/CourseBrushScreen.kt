package com.fuck.ewt.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.fuck.ewt.data.AppContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/** 未刷课时条目 */
data class BrushTask(
    val homeworkId: String,
    val lessonId: String,
    val title: String,
    val subject: String,
    val duration: Int,
)

/**
 * 课程页：Python 刷课（Chaquopy 内嵌 ewt_brush_v2）。
 * 通过「刷指定课程」二级页选择队列；开始/暂停；日志固定框内滚动。
 * 队列刷：lesson_filter 逗号分隔多值，脚本内部按 concurrency 批量并行刷多课。
 */
@Composable
fun CourseBrushScreen(
    settings: BrushSettings,
    onOpenCoursePick: () -> Unit,
) {
    val context = LocalContext.current
    val logFile = remember { File(context.filesDir, "brush.log") }
    val listState = rememberLazyListState()
    val logScroll = rememberScrollState()

    // 状态提升到全局（跨 Tab 切换保持）
    var logText by remember { mutableStateOf(CourseState.logText) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(CourseState.showDetail) }
    var showLogMenu by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf<List<BrushTask>?>(CourseState.tasks) }
    var selectedLesson by remember { mutableStateOf<String?>(CourseState.selectedLesson) }
    // 指定课程队列（来自 CoursePickScreen）
    val pickQueue = remember { mutableStateOf(CourseState.pickQueue) }
    val pauseFile = remember { File(context.filesDir, "pause.flag") }

    // 自动获取主 App 已登录 token（不显示明文）
    val hasToken = remember { !AppContainer.tokenStore.load().isNullOrBlank() }

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

    // 日志自动滑到底部（固定框内）
    LaunchedEffect(logText, showDetail) {
        logScroll.scrollTo(logScroll.maxValue)
    }

    // 同步状态到全局（跨 Tab 保持）
    LaunchedEffect(logText) { CourseState.logText = logText }
    LaunchedEffect(tasks) { CourseState.tasks = tasks }
    LaunchedEffect(selectedLesson) { CourseState.selectedLesson = selectedLesson }
    LaunchedEffect(showDetail) { CourseState.showDetail = showDetail }

    /**
     * 批量刷队列：一次性传逗号分隔的 lessonId 给脚本，脚本内部按 concurrency 批量并行刷多课。
     * 并发路数 = 设置值（每批 gather 多个课时同时刷），每课内部还有 burst 爆发加速。
     */
    fun brushQueue(queue: List<BrushTask>) {
        if (running || queue.isEmpty()) return
        val token = AppContainer.tokenStore.load()
        // 逗号分隔多值 lesson_filter（脚本已支持），脚本自身 batch 并行
        val lessonFilter = queue.joinToString(",") { it.lessonId }
        running = true
        paused = false
        pauseFile.delete()
        handler.post {
            logText += "\n▶ 并行刷 ${queue.size} 个课时（并发路数 ${settings.concurrency}，脚本内 batch 并行）\n"
        }
        thread {
            try {
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = logFile.absolutePath
                val ret: Int = if (!token.isNullOrBlank()) {
                    mod.callAttr(
                        "run_brush_token", logPath, token, "", "", lessonFilter,
                        settings.concurrency, settings.qps, false, settings.burst, settings.forceAll,
                    ).toInt()
                } else {
                    mod.callAttr(
                        "run_brush", logPath, "", "", lessonFilter,
                        settings.concurrency, settings.qps, false, settings.burst, settings.forceAll,
                    ).toInt()
                }
                handler.post { logText += "\n==== 队列刷结束，返回码 $ret ====\n" }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
                paused = false
                pauseFile.delete()
            }
        }
    }

    /** 刷全部课程（无队列时，用设置的总并发） */
    fun brushAll() {
        if (running) return
        val token = AppContainer.tokenStore.load()
        running = true
        paused = false
        pauseFile.delete()
        thread {
            try {
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = logFile.absolutePath
                val ret: Int = if (!token.isNullOrBlank()) {
                    mod.callAttr(
                        "run_brush_token", logPath, token, "", "", "",
                        settings.concurrency, settings.qps, false, settings.burst, settings.forceAll,
                    ).toInt()
                } else {
                    mod.callAttr(
                        "run_brush", logPath, "", "", "",
                        settings.concurrency, settings.qps, false, settings.burst, settings.forceAll,
                    ).toInt()
                }
                handler.post { logText += "\n==== 结束，返回码 $ret ====\n" }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
                paused = false
                pauseFile.delete()
            }
        }
    }

    fun togglePause() {
        if (!running) return
        paused = !paused
        if (paused) {
            runCatching { pauseFile.writeText("pause") }
            logText += "\n⏸ 已暂停，点击继续恢复刷课\n"
        } else {
            pauseFile.delete()
            logText += "\n▶ 继续刷课\n"
        }
    }

    // ── 摘要算法：过滤噪音行，保留关键进度 ──
    val summaryText = remember(logText) {
        logText.lineSequence()
            .map { it.trimEnd() }
            .filter { line ->
                val t = line.trim()
                t.isNotEmpty() &&
                    !t.startsWith("[BFE-REQ]") &&
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
            // 大标题行（miuix 原生排版）
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
            // 登录态
            item(key = "token_status") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (hasToken) "已登录" else "未检测到登录 token，请先在「试卷」页登录",
                        fontSize = 12.sp,
                        color = if (hasToken) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                    )
                }
            }
            // 刷指定课程入口（全宽）
            item(key = "pick_btn") {
                Button(
                    onClick = onOpenCoursePick,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "刷指定课程",
                        fontSize = 14.sp,
                    )
                }
            }
            // 已选队列提示（来自刷指定课程二级页）
            if (pickQueue.value.isNotEmpty()) {
                item(key = "pick_queue") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "已选 ${pickQueue.value.size} 个指定课程：${pickQueue.value.joinToString("、") { it.title }}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "清除",
                                onClick = {
                                    CourseState.pickQueue = emptyList()
                                    pickQueue.value = emptyList()
                                },
                            )
                        }
                    }
                }
            }
            // 参数摘要（只读）
            item(key = "params_summary") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "并行 ${settings.concurrency} · QPS ${settings.qps} · 爆发 ${settings.burst}（在「设置」页修改）",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
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
                        onClick = {
                            if (running) {
                                togglePause()
                            } else {
                                // 有指定课程队列则刷队列，否则刷全部
                                if (pickQueue.value.isNotEmpty()) {
                                    brushQueue(pickQueue.value)
                                } else {
                                    brushAll()
                                }
                            }
                        },
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = when {
                                running && paused -> "继续"
                                running -> "暂停"
                                pickQueue.value.isNotEmpty() -> "开始刷已选的 ${pickQueue.value.size} 个课程"
                                else -> "刷全部课程"
                            },
                            fontSize = 14.sp,
                        )
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
                    // 三条杠 → 日志菜单（Detail / 清空）
                    Box {
                        IconButton(onClick = { showLogMenu = true }) {
                            Text(
                                text = "☰",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                        if (showLogMenu) {
                            LogMenuPopup(
                                showDetail = showDetail,
                                onDetail = { showDetail = !showDetail; showLogMenu = false },
                                onClear = {
                                    logFile.delete()
                                    logText = ""
                                    lastLen = 0
                                    showLogMenu = false
                                },
                                onDismiss = { showLogMenu = false },
                            )
                        }
                    }
                }
            }
            item(key = "log") {
                // 固定高度日志框：内部滚动（无边框，仅底色）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.06f))
                        .verticalScroll(logScroll)
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

/** 日志菜单：三条杠弹出（Detail 切换 / 清空），纯文字 + 分割线 */
@Composable
private fun LogMenuPopup(
    showDetail: Boolean,
    onDetail: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
    }
    val p = enter.value
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = Modifier
                .width(140.dp)
                .graphicsLayer {
                    alpha = p
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                },
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Column {
                MenuItem(text = if (showDetail) "Summary" else "Detail", onClick = onDetail)
                // 分割线
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f)),
                )
                MenuItem(text = "清空", onClick = onClear)
            }
        }
    }
}

/** 菜单项：文字 + 点击涟漪，无按钮背景 */
@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 课程页跨 Tab 保持的状态（AppRoot 不销毁，组件重建时恢复） */
object CourseState {
    var logText: String = ""
    var tasks: List<BrushTask>? = null
    var selectedLesson: String? = null
    var showDetail: Boolean = false
    /** 刷指定课程队列（来自 CoursePickScreen） */
    var pickQueue: List<BrushTask> = emptyList()
}
