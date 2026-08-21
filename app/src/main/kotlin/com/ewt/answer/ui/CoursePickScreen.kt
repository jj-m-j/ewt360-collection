package com.ewt.answer.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import kotlin.concurrent.thread

/**
 * 刷指定课程二级页：进入自动扫描全部未刷课程，点击列表项加入刷课队列（勾选提醒），
 * 底部悬浮蓝色保存按钮；未保存返回时弹询问框。
 */
@Composable
fun CoursePickScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var tasks by remember { mutableStateOf<List<BrushTask>?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    // 已选队列（lessonId 集合，从全局队列恢复）
    var selectedIds by remember { mutableStateOf<Set<String>>(CourseState.pickQueue.map { it.lessonId }.toSet()) }
    // 初始选择（用于判断是否有改动）
    val initialIds = remember { CourseState.pickQueue.map { it.lessonId }.toSet() }
    // 是否显示保存询问
    var showSaveDialog by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    fun scan() {
        scanning = true
        scanError = null
        thread {
            try {
                val token = AppContainer.tokenStore.load()
                val py = Python.getInstance()
                val mod = py.getModule("brush_app")
                val logPath = File(context.filesDir, "brush.log").absolutePath
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
                handler.post {
                    tasks = parsed
                    CourseState.tasks = parsed
                    scanning = false
                }
            } catch (e: Throwable) {
                handler.post {
                    scanError = e.message ?: "扫描失败"
                    scanning = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { scan() }

    /** 保存选择并返回首页 */
    fun saveAndBack() {
        CourseState.pickQueue = tasks?.filter { it.lessonId in selectedIds } ?: emptyList()
        onBack()
    }

    /** 返回：有改动则询问是否保存，无改动直接返回 */
    fun attemptBack() {
        if (selectedIds != initialIds) {
            showSaveDialog = true
        } else {
            onBack()
        }
    }

    // 系统返回键：拦截并走询问逻辑
    BackHandler {
        if (showSaveDialog) {
            showSaveDialog = false
        } else {
            attemptBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "刷指定课程",
                subtitle = "点击未刷课程加入队列",
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.rotate(180f),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 已选提醒条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedIds.isEmpty()) "尚未选择课程" else "已选 ${selectedIds.size} 门课程，点击右下角保存",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "清除",
                        onClick = { selectedIds = emptySet() },
                    )
                }

                when {
                    scanning -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "扫描未刷课程中…",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    scanError != null -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = scanError ?: "",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(text = "重试", onClick = { scan() })
                        }
                    }
                    tasks == null || tasks!!.isEmpty() -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "没有未刷课程",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 100.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item(key = "title") {
                                SmallTitle(text = "未刷课程（${tasks!!.size}）")
                            }
                            items(tasks!!, key = { it.lessonId }) { t ->
                                val isChecked = t.lessonId in selectedIds
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    onClick = {
                                        selectedIds = if (isChecked) selectedIds - t.lessonId else selectedIds + t.lessonId
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 勾选指示
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
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = t.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isChecked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "${t.subject} · ${t.duration / 60}min",
                                                fontSize = 11.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部悬浮蓝色保存按钮（固定位置）
            Button(
                onClick = { saveAndBack() },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .height(48.dp),
            ) {
                Text(
                    text = "保存已选的 ${selectedIds.size} 个课程",
                    fontSize = 14.sp,
                )
            }
        }
    }

    // 未保存返回询问：蓝色保存在上、白色不保存在下
    if (showSaveDialog) {
        WindowDialog(
            show = true,
            title = "尚未保存选择",
            summary = "返回首页前是否保存？",
            onDismissRequest = { showSaveDialog = false },
        ) {
            Column {
                Text(
                    text = "你已修改课程选择但尚未保存。保存后首页开始刷课将使用已选的 ${selectedIds.size} 个课程。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        showSaveDialog = false
                        saveAndBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("保存", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "不保存",
                    onClick = {
                        showSaveDialog = false
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            }
        }
    }
}
