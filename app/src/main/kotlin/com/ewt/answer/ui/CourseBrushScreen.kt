package com.ewt.answer.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.shadow
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
import top.yukonga.miuix.kmp.basic.Switch
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

/** 日志菜单项 */
private enum class LogAction { Detail, Clear }

/**
 * 课程页：Python 刷课（Chaquopy 内嵌 ewt_brush_v2）。
 * 自动读取主 App 已登录 token；可扫描未刷课程（长按多选批量刷）、开始/暂停；日志固定框内滚动。
 */
@Composable
fun CourseBrushScreen(
    settings: BrushSettings,
) {
    val context = LocalContext.current
    val logFile = remember { File(context.filesDir, "brush.log") }
    val listState = rememberLazyListState()
    val logScroll = rememberScrollState()

    // 状态提升到全局（跨 Tab 切换保持）
    var logText by remember { mutableStateOf(CourseState.logText) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var dryRun by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(CourseState.showDetail) }
    var showLogMenu by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf<List<BrushTask>?>(CourseState.tasks) }
    var scanning by remember { mutableStateOf(false) }
    var selectedLesson by remember { mutableStateOf<String?>(CourseState.selectedLesson) }
    // 多选模式
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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

    /** 批量刷：选中的课程依次刷取 */
    fun brushSelected() {
        if (running || selectedIds.isEmpty()) return
        val token = AppContainer.tokenStore.load()
        val queue = (tasks ?: emptyList()).filter { it.lessonId in selectedIds }
        running = true
        paused = false
        pauseFile.delete()
        thread {
            try {
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = logFile.absolutePath
                queue.forEachIndexed { i, t ->
                    if (pauseFile.exists()) {
                        // 暂停等待
                        while (pauseFile.exists()) {
                            Thread.sleep(500)
                        }
                    }
                    handler.post {
                        logText += "\n▶ 队列 ${i + 1}/${queue.size}：${t.title}\n"
                    }
                    val ret: Int = if (!token.isNullOrBlank()) {
                        mod.callAttr(
                            "run_brush_token", logPath, token, "", "", "",
                            t.lessonId, settings.concurrency, settings.qps, dryRun, settings.burst,
                        ).toInt()
                    } else {
                        mod.callAttr(
                            "run_brush", logPath, "", "", "",
                            settings.concurrency, settings.qps, dryRun, settings.burst,
                        ).toInt()
                    }
                    handler.post { logText += "\n==== ${t.title} 结束，返回码 $ret ====\n" }
                }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
                paused = false
                pauseFile.delete()
                handler.post {
                    selectMode = false
                    selectedIds = emptySet()
                }
            }
        }
    }

    fun runPy(fn: String, task: BrushTask? = null) {
        if (running) return
        val token = AppContainer.tokenStore.load()

        running = true
        paused = false
        pauseFile.delete()
        if (fn == "scan") { scanning = true }
        thread {
            try {
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = logFile.absolutePath
                if (fn == "scan") {
                    val json = mod.callAttr("scan_tasks", logPath, token ?: "").toString()
                    val parsed = runCatching {
                        Json.parseToJsonElement(json).jsonArray.map { el ->
                            val o = el.jsonObject
                            BrushTask(
                                homeworkId = o["homeworkId"]?.jsonPrimitive?.contentOrNull ?: "",
                                lessonId = o["lessonId"]?.jsonPrimitive?.contentOrNull ?: "",
                                title = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
                                subject = o["subject"]?.jsonPrimitive?.contentOrNull ?: "",
                                duration = o["duration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                            )
                        }
                    }.getOrNull() ?: emptyList()
                    handler.post { tasks = parsed }
                } else {
                    val ret: Int = if (!token.isNullOrBlank()) {
                        mod.callAttr(
                            "run_brush_token", logPath, token, "", "", "",
                            task?.lessonId ?: "", settings.concurrency, settings.qps, dryRun, settings.burst,
                        ).toInt()
                    } else {
                        mod.callAttr(
                            "run_brush", logPath, "", "", "",
                            settings.concurrency, settings.qps, dryRun, settings.burst,
                        ).toInt()
                    }
                    handler.post { logText += "\n==== 结束，返回码 $ret ====\n" }
                }
            } catch (e: Throwable) {
                handler.post { logText += "\n==== 调用异常: ${e.javaClass.simpleName}: ${e.message} ====\n" }
            } finally {
                running = false
                paused = false
                pauseFile.delete()
                scanning = false
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
            // 登录态 + 仅扫描
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
                            text = if (hasToken) "已登录" else "未检测到登录 token，请先在「试卷」页登录",
                            fontSize = 12.sp,
                            color = if (hasToken) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
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
            // 扫描未刷课程
            item(key = "scan_btn") {
                Button(
                    onClick = { runPy("scan") },
                    enabled = !running && !scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (scanning) "扫描中…" else "扫描未刷课程",
                        fontSize = 14.sp,
                    )
                }
            }
            // 未刷课程列表（进场动画 + 长按多选）
            tasks?.let { list ->
                if (list.isEmpty()) {
                    item(key = "no_tasks") {
                        Text(
                            text = "没有未刷课程",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    item(key = "task_title") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallTitle(text = "未刷课程（${list.size}）")
                            Spacer(Modifier.weight(1f))
                            if (selectMode) {
                                TextButton(
                                    text = "全选",
                                    onClick = {
                                        selectedIds = list.map { it.lessonId }.toSet()
                                    },
                                )
                            }
                        }
                    }
                    list.forEachIndexed { index, t ->
                        item(key = "task_${t.lessonId}") {
                            val isSel = selectedLesson == t.lessonId
                            val isChecked = t.lessonId in selectedIds
                            // 进场动画
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    onClick = {
                                        if (selectMode) {
                                            selectedIds = if (isChecked) selectedIds - t.lessonId else selectedIds + t.lessonId
                                        } else {
                                            selectedLesson = t.lessonId
                                            runPy("brush", t)
                                        }
                                    },
                                    onLongClick = {
                                        // 长按进入多选
                                        selectMode = true
                                        selectedIds = setOf(t.lessonId)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 多选模式：左侧勾选指示
                                        if (selectMode) {
                                            Box(
                                                Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isChecked) MiuixTheme.colorScheme.primary
                                                        else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isChecked) {
                                                    Text(
                                                        text = "✓",
                                                        fontSize = 12.sp,
                                                        color = MiuixTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = t.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSel || isChecked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "${t.subject} · ${t.duration / 60}min",
                                                fontSize = 11.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                        if (isSel && !selectMode) {
                                            Text(
                                                text = "刷课中…",
                                                fontSize = 11.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 多选操作栏
                    if (selectMode) {
                        item(key = "select_bar") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it },
                                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    TextButton(
                                        text = "取消",
                                        onClick = {
                                            selectMode = false
                                            selectedIds = emptySet()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = { brushSelected() },
                                        enabled = selectedIds.isNotEmpty() && !running,
                                        colors = ButtonDefaults.buttonColors(
                                            color = MiuixTheme.colorScheme.primary,
                                            contentColor = MiuixTheme.colorScheme.onPrimary,
                                        ),
                                        modifier = Modifier.weight(2f),
                                    ) {
                                        Text(
                                            text = "刷选中（${selectedIds.size}）",
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
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
                        onClick = { if (running) togglePause() else runPy("brush") },
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
                                else -> "开始刷课"
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

/** 日志菜单：三条杠弹出（Detail 切换 / 清空），纯文字 + 分割线 + 原生阴影 */
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
                .shadow(16.dp, RoundedCornerShape(16.dp), clip = false)
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
}
