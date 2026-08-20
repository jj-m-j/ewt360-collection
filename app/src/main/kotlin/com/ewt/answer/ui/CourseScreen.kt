package com.ewt.answer.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.CourseRepository
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课程页：原生视频课刷课。顶栏与试卷/设置页统一（build174 同款 miuix TopAppBar + 上滑收缩）。
 */
@Composable
fun CourseScreen(
    onOpenSettings: () -> Unit = {},
) {
    val vm: com.ewt.answer.ui.CourseViewModel = viewModel(
        factory = com.ewt.answer.ui.CourseViewModel.Factory,
    )
    val uiState by vm.uiState.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val brushingAll by vm.brushingAll.collectAsState()
    val summary by vm.summary.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val context = LocalContext.current

    val topBarBackdrop = rememberLayerBackdrop()
    val glassSurface = MiuixTheme.colorScheme.surface
    val listState = rememberLazyListState()

    // build174 同款：miuix 原生 TopAppBar + 上滑收缩
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ewt_prefs", android.content.Context.MODE_PRIVATE)
        CourseRepository.burstSize = prefs.getInt("course_burst", 1)
        vm.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "课程",
                subtitle = "当前并发 ${CourseRepository.burstSize} 路",
                titlePadding = 16.dp,
                modifier = Modifier.drawBackdrop(
                    backdrop = topBarBackdrop,
                    shape = { RectangleShape },
                    effects = { blur(10f.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(glassSurface.copy(alpha = 0.62f))
                    },
                ),
                color = Color.Transparent,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = SettingsTabIcon,
                            contentDescription = "课程设置",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(topBarBackdrop),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 状态条 + 操作行（随页面滑动，非顶栏部分）
                item(key = "course_status") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = when {
                                brushingAll && summary.isNotBlank() -> "批量刷课中：$summary"
                                uiState is com.ewt.answer.ui.CourseViewModel.UiState.Loading -> statusText.ifBlank { "扫描中…" }
                                else -> "点击课时开始刷课（当前并发 ${CourseRepository.burstSize} 路）"
                            },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
                item(key = "course_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { vm.brushAll() },
                            enabled = !brushingAll && uiState is com.ewt.answer.ui.CourseViewModel.UiState.Ready,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (brushingAll) "批量刷课中…" else "刷全部未完成", fontSize = 14.sp)
                        }
                        if (brushingAll) {
                            Spacer(Modifier.width(10.dp))
                            TextButton(
                                text = "停止",
                                onClick = { vm.stop() },
                            )
                        }
                    }
                }

                when (val state = uiState) {
                    com.ewt.answer.ui.CourseViewModel.UiState.Loading -> {
                        item(key = "course_loading") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        if (statusText.isNotBlank()) {
                            item(key = "course_loading_text") {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                    is com.ewt.answer.ui.CourseViewModel.UiState.Error -> {
                        item(key = "course_error") {
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
                                TextButton(
                                    text = "重试",
                                    onClick = { vm.load(force = true) },
                                )
                            }
                        }
                    }
                    is com.ewt.answer.ui.CourseViewModel.UiState.Ready -> {
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
