package com.ewt.answer.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课程页：原生视频课刷课（默认）。网页模式已独立为 [WebCourseScreen]（脱离主层 backdrop，避免闪烁）。
 */
@Composable
fun CourseScreen(
    onOpenWeb: () -> Unit = {},
) {
    val vm: com.ewt.answer.ui.CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.ewt.answer.ui.CourseViewModel.Factory,
    )
    val uiState by vm.uiState.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val brushingAll by vm.brushingAll.collectAsState()
    val summary by vm.summary.collectAsState()
    val statusText by vm.statusText.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "课程",
                titlePadding = 16.dp,
                actions = {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = "网页模式",
                        onClick = onOpenWeb,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 状态条
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = when {
                        brushingAll && summary.isNotBlank() -> "批量刷课中：$summary"
                        uiState is com.ewt.answer.ui.CourseViewModel.UiState.Loading -> statusText.ifBlank { "扫描中…" }
                        else -> "点击课时开始刷课；竞态爆发上报，约 1 小时课时 1~2 分钟刷完"
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            // 操作行
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { vm.brushAll() },
                    enabled = !brushingAll && uiState is com.ewt.answer.ui.CourseViewModel.UiState.Ready,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (brushingAll) "批量刷课中…" else "刷全部未完成", fontSize = 14.sp)
                }
                if (brushingAll) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = "停止",
                        onClick = { vm.stop() },
                    )
                }
            }

            when (val state = uiState) {
                com.ewt.answer.ui.CourseViewModel.UiState.Loading -> {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                    }
                    if (statusText.isNotBlank()) {
                        Text(
                            text = statusText,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
                is com.ewt.answer.ui.CourseViewModel.UiState.Error -> {
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.message,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = "重试",
                            onClick = { vm.load() },
                        )
                    }
                }
                is com.ewt.answer.ui.CourseViewModel.UiState.Ready -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    ) {
                        state.groups.forEach { group ->
                            item(key = "group_${group.homeworkTitle}") {
                                top.yukonga.miuix.kmp.basic.SmallTitle(text = group.homeworkTitle)
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

/** 单课时卡片 */
@Composable
private fun LessonCard(
    ui: com.ewt.answer.ui.CourseViewModel.LessonUi?,
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
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(3.dp))
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                if (running) {
                    Text(
                        text = if (percent > 0) "$percent%" else "…",
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = when {
                            done || lesson.finished -> MiuixTheme.colorScheme.primary
                            failed -> MiuixTheme.colorScheme.error
                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = "刷课",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (running || percent > 0 || ui?.message?.isNotBlank() == true) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
                    progress = (percent / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui?.message?.isNotBlank() == true) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Text(
                        text = ui.message,
                        fontSize = 11.sp,
                        color = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 网页模式独立页：WebView + 注入刷课助手脚本（EWT360-Helper，兜底）。脱离主层 backdrop，不闪烁。 */
@Composable
fun WebCourseScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var injected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("加载中…") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "课程 · 网页模式",
                titlePadding = 16.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "刷课助手：$status\n打开课程视频后，点击页面右下角 📚 图标可开启 自动跳题 / 自动连播 / 自动过检 / 2倍速 / 刷课模式。",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
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
}
