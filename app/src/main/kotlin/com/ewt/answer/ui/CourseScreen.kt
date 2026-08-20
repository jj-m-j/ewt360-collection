package com.ewt.answer.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课程页：原生视频课刷课（默认）+ 网页模式（WebView 注入兜底）。
 * 原生模式：扫描视频课时 → 点击/一键刷全部 → 竞态爆发播放上报，进度实时显示。
 */
@Composable
fun CourseScreen() {
    val vm: CourseViewModel = viewModel(factory = CourseViewModel.Factory)
    val uiState by vm.uiState.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val brushingAll by vm.brushingAll.collectAsState()
    val summary by vm.summary.collectAsState()
    val statusText by vm.statusText.collectAsState()
    var webMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "课程",
                titlePadding = 16.dp,
                actions = {
                    TextButton(text = "网页模式") { webMode = true }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (webMode) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "网页模式：注入刷课助手脚本（自动连播/过检/跳题/倍速）",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(text = "返回原生刷课") { webMode = false }
                    }
                    WebCourseView(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    // 状态条
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Text(
                                text = when {
                                    brushingAll && summary.isNotBlank() -> "批量刷课中：$summary"
                                    uiState is CourseViewModel.UiState.Loading -> statusText.ifBlank { "扫描中…" }
                                    else -> "点击课时开始刷课；竞态爆发上报，约 1 小时课时 1~2 分钟刷完"
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                    // 操作行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { vm.brushAll() },
                            enabled = !brushingAll && uiState is CourseViewModel.UiState.Ready,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (brushingAll) "批量刷课中…" else "刷全部未完成", fontSize = 14.sp)
                        }
                        if (brushingAll) {
                            Spacer(Modifier.width(10.dp))
                            TextButton(text = "停止", onClick = { vm.stop() })
                        }
                    }

                    when (val state = uiState) {
                        CourseViewModel.UiState.Loading -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                            if (statusText.isNotBlank()) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                        is CourseViewModel.UiState.Error -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    state.message,
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(text = "重试", onClick = { vm.load() })
                            }
                        }
                        is CourseViewModel.UiState.Ready -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 24.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                state.groups.forEach { group ->
                                    item(key = "group_${group.homeworkTitle}") {
                                        SmallTitle(text = group.homeworkTitle)
                                    }
                                    items(group.lessons, key = { "lesson_${it.lessonId}" }) { lesson ->
                                        val ui = lessons[lesson.lessonId]
                                        LessonCard(
                                            ui = ui,
                                            onClick = { vm.brushLesson(lesson) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单课时卡片 */
@Composable
private fun LessonCard(
    ui: CourseViewModel.LessonUi?,
    onClick: () -> Unit,
) {
    val running = ui?.running == true
    val done = ui?.done == true
    val failed = ui?.failed == true
    val lesson = ui?.lesson ?: return
    val percent = ui?.percent ?: 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    val durText = if (lesson.durationSec > 0) {
                        val min = lesson.durationSec / 60
                        if (min > 0) "约 $min 分钟" else "${lesson.durationSec} 秒"
                    } else {
                        ""
                    }
                    Text(
                        text = listOfNotNull(
                            lesson.subjectName.takeIf { it.isNotBlank() },
                            durText,
                            if (lesson.mustLearn) "必学" else null,
                        ).joinToString(" · ").ifBlank { "视频课时" },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (running) {
                    Text(
                        text = if (percent > 0) "$percent%" else "…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = when {
                            done -> "已完成"
                            failed -> "失败"
                            lesson.finished -> "已完成"
                            else -> "未开始"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            done || lesson.finished -> MiuixTheme.colorScheme.primary
                            failed -> MiuixTheme.colorScheme.error
                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = "刷课",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (running || percent > 0 || ui?.message?.isNotBlank() == true) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = (percent / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui?.message?.isNotBlank() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ui.message,
                        fontSize = 11.sp,
                        color = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 网页模式：WebView + 注入刷课助手脚本（EWT360-Helper，兜底） */
@Composable
private fun WebCourseView(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var injected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("加载中…") }

    Column(modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "刷课助手：$status\n打开课程视频后，点击页面右下角 📚 图标可开启 自动跳题 / 自动连播 / 自动过检 / 2倍速 / 刷课模式。",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    @SuppressLint("SetJavaScriptEnabled")
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (!injected) {
                                    injected = true
                                    val js = runCatching {
                                        ctx.assets.open("ewt_helper.js").bufferedReader().readText()
                                    }.getOrNull()
                                    if (!js.isNullOrBlank()) {
                                        view?.evaluateJavascript(js, null)
                                        status = "已注入（打开视频后点右下角 📚）"
                                    } else {
                                        status = "脚本加载失败"
                                    }
                                }
                            }
                        }
                        loadUrl("https://web.ewt360.com/site-study/")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
