package com.fuck.ewt.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuck.ewt.data.Paper
import com.fuck.ewt.data.UserInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun HomeScreen(
    userInfo: UserInfo?,
    paperCounts: Map<String, Int>,
    listState: LazyListState,
    onOpenPaper: (Paper) -> Unit,
    onOpenLinkQuery: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val uiState by vm.uiState.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val dateFilter by vm.dateFilter.collectAsState()
    val subjectFilter by vm.subjectFilter.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val brushing by vm.brushing.collectAsState()
    val brushProgress by vm.brushProgress.collectAsState()
    val brushResult by vm.brushResult.collectAsState()
    val brushPaused by vm.brushPaused.collectAsState()

    var showBrushDialog by remember { mutableStateOf(false) }

    val searchState = rememberTextFieldState()
    LaunchedEffect(searchQuery) {
        if (searchState.text.toString() != searchQuery) {
            searchState.edit { replace(0, length, searchQuery) }
        }
    }
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }
            .distinctUntilChanged()
            .collect { vm.setSearchQuery(it) }
    }

    LaunchedEffect(Unit) { vm.load() }

    // ── 筛选计算（基于已加载试卷） ──
    val readyGroups = (uiState as? HomeViewModel.UiState.Ready)?.groups
    val allPapers = remember(readyGroups) { readyGroups?.flatMap { it.papers } ?: emptyList() }
    val dates = remember(allPapers) {
        allPapers.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }
            .distinct().sortedDescending()
    }
    val subjects = remember(allPapers) {
        allPapers.mapNotNull { it.subjectName.takeIf { s -> s.isNotBlank() } }
            .distinct().sorted()
    }
    val filteredGroups = remember(readyGroups, dateFilter, subjectFilter) {
        readyGroups?.mapNotNull { g ->
            val fp = g.papers.filter { p ->
                (dateFilter == null || p.date == dateFilter) &&
                    (subjectFilter == null || p.subjectName == subjectFilter)
            }
            if (fp.isEmpty()) null else g.copy(papers = fp)
        } ?: emptyList()
    }
    val searchLower = remember(searchQuery) { searchQuery.trim().lowercase() }
    val dateGroups = remember(filteredGroups, searchLower) {
        filteredGroups.flatMap { it.papers }
            .filter { p ->
                searchLower.isEmpty() ||
                    p.title.lowercase().contains(searchLower) ||
                    p.homeworkTitle.lowercase().contains(searchLower) ||
                    p.subjectName.lowercase().contains(searchLower)
            }
            .groupBy { it.date.ifBlank { "其他" } }
            .toSortedMap(compareByDescending { it })
    }

    // 一键刷今日可选日期：优先试卷实际日期，否则最近 7 天
    val brushDates = remember(allPapers) {
        val fromPapers = allPapers.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }
            .distinct().sortedDescending()
        if (fromPapers.isNotEmpty()) fromPapers else recentDays(7)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { vm.load(force = true) },
            contentPadding = PaddingValues(top = 56.dp),
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
                    bottom = 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 大标题行（miuix 原生排版，与课程/设置页统一）
                item(key = "large_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 0.dp, top = 4.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "试卷列表",
                            fontSize = MiuixTheme.textStyles.title1.fontSize,
                            fontWeight = FontWeight.Normal,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // 信息结构：大标题 → 搜索框 → 粘贴链接 → 一键刷今日 → 列表
                item(key = "search") {
                    TextField(
                        state = searchState,
                        label = "搜索试卷 / 课后习题",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "link") {
                    LinkQueryEntry(onClick = onOpenLinkQuery)
                }
                item(key = "brush_today") {
                    BrushTodayEntry(
                        brushing = brushing,
                        paused = brushPaused,
                        progress = brushProgress,
                        onClick = { if (!brushing) showBrushDialog = true },
                        onTogglePause = { vm.toggleBrushPause() },
                    )
                }

                when (val state = uiState) {
                    HomeViewModel.UiState.Loading -> {
                        if (statusText.isNotBlank()) {
                            item(key = "loading_text") {
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
                    HomeViewModel.UiState.Empty -> {
                        item(key = "empty") {
                            EmptyHint("暂未找到可查询的任务\n请确认作业已布置试卷，或使用粘贴链接查询")
                        }
                    }
                    is HomeViewModel.UiState.Error -> {
                        item(key = "error") {
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
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(text = "重试", onClick = { vm.load(force = true) })
                            }
                        }
                    }
                    is HomeViewModel.UiState.Ready -> {
                        if (dateGroups.isEmpty()) {
                            item(key = "filtered_empty") {
                                EmptyHint("当前筛选 / 搜索条件下没有任务")
                            }
                        } else {
                            // 第一个日期标题右侧放三条杠（☰ 与课程页一致）
                            dateGroups.entries.forEachIndexed { idx, (date, papers) ->
                                item(key = "date_$date") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SmallTitle(
                                            text = if (date == "其他") "未分类" else date,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (idx == 0) {
                                            FilterRow(
                                                dateFilter = dateFilter,
                                                subjectFilter = subjectFilter,
                                                dates = dates,
                                                subjects = subjects,
                                                onDateSelect = { vm.setDateFilter(it) },
                                                onSubjectSelect = { vm.setSubjectFilter(it) },
                                                onClear = {
                                                    vm.setDateFilter(null)
                                                    vm.setSubjectFilter(null)
                                                },
                                            )
                                        }
                                    }
                                }
                                // 试卷列表：相邻试卷间加分割线
                                itemsIndexed(papers, key = { _, p -> p.paperId }) { i, paper ->
                                    Column {
                                        PaperRow(
                                            paper = paper,
                                            count = paperCounts[paper.paperId],
                                            onClick = { onOpenPaper(paper) },
                                        )
                                        if (i < papers.lastIndex) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(0.5.dp)
                                                    .padding(horizontal = 16.dp)
                                                    .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.15f)),
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

    // 一键刷今日：日期滚轮选择
    if (showBrushDialog) {
        BrushDateDialog(
            dateOptions = brushDates,
            initialIndex = brushDates.indexOf(formatToday()).coerceAtLeast(0),
            onDismiss = { showBrushDialog = false },
            onStart = { date ->
                showBrushDialog = false
                vm.brushToday(date)
            },
        )
    }

    // 刷卷结果
    brushResult?.let { result ->
        WindowDialog(
            show = true,
            title = "刷卷结果",
            summary = "一键刷今日试卷",
            onDismissRequest = { vm.clearBrushResult() },
        ) {
            Column {
                Text(
                    text = result,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { vm.clearBrushResult() },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("知道了", fontSize = 14.sp)
                }
            }
        }
    }
}

// ── 三条杠 + 锚点弹层 ───

/** 三条杠（☰ 样式与课程页一致）+ 筛选弹层 */
@Composable
private fun FilterRow(
    dateFilter: String?,
    subjectFilter: String?,
    dates: List<String>,
    subjects: List<String>,
    onDateSelect: (String?) -> Unit,
    onSubjectSelect: (String?) -> Unit,
    onClear: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val btnScale = remember { Animatable(1f) }
    var popupVisible by remember { mutableStateOf(false) }
    var popupExiting by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = {
                scope.launch {
                    btnScale.animateTo(0.97f, tween(60, easing = LinearEasing))
                    btnScale.animateTo(1f, tween(60, easing = LinearEasing))
                }
                popupExiting = false
                popupVisible = true
            },
        ) {
            // ☰ 字符，与课程页三条杠一致
            Text(
                text = "☰",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = btnScale.value
                    scaleY = btnScale.value
                },
            )
        }
        if (popupVisible) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { 6.dp.roundToPx() }),
                onDismissRequest = { if (!popupExiting) popupExiting = true },
                properties = PopupProperties(focusable = true),
            ) {
                FilterPopupCard(
                    exiting = popupExiting,
                    onExitFinished = {
                        popupVisible = false
                        popupExiting = false
                    },
                    dateFilter = dateFilter,
                    subjectFilter = subjectFilter,
                    dates = dates,
                    subjects = subjects,
                    onDateSelect = onDateSelect,
                    onSubjectSelect = onSubjectSelect,
                    onClear = onClear,
                )
            }
        }
    }
}

private enum class FilterPane { Main, Date, Subject }

/** 三条杠弹层：主面板矮、滚轮面板高（高度即时切换，无大小过渡动画） */
@Composable
private fun FilterPopupCard(
    exiting: Boolean,
    onExitFinished: () -> Unit,
    dateFilter: String?,
    subjectFilter: String?,
    dates: List<String>,
    subjects: List<String>,
    onDateSelect: (String?) -> Unit,
    onSubjectSelect: (String?) -> Unit,
    onClear: () -> Unit,
) {
    var pane by remember { mutableStateOf(FilterPane.Main) }
    var draftDate by remember(dateFilter) { mutableStateOf(dateFilter) }
    var draftSubject by remember(subjectFilter) { mutableStateOf(subjectFilter) }

    val dateOptions = remember(dates) { listOf<String?>(null) + dates }
    val subjectOptions = remember(subjects) { listOf<String?>(null) + subjects }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!exiting) enter.animateTo(1f, tween(180, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(exiting) {
        if (exiting) {
            enter.animateTo(0f, tween(120, easing = FastOutLinearInEasing))
            onExitFinished()
        }
    }
    val p = enter.value

    Card(
        modifier = Modifier
            .width(200.dp)
            .graphicsLayer {
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
                transformOrigin = TransformOrigin(1f, 0f)
            },
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = pane,
            transitionSpec = {
                (fadeIn(tween(120)) + slideInHorizontally(tween(140)) { it / 4 })
                    .togetherWith(fadeOut(tween(90)) + slideOutHorizontally(tween(110)) { -it / 4 })
            },
            label = "filter_pane",
        ) { p2 ->
            Column {
                when (p2) {
                    FilterPane.Main -> {
                        Text(
                            text = "筛选",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        FilterOptionRow(
                            label = "日期",
                            value = dateFilter ?: "全部",
                            onClick = { pane = FilterPane.Date },
                        )
                        FilterOptionRow(
                            label = "学科",
                            value = subjectFilter ?: "全部",
                            onClick = { pane = FilterPane.Subject },
                        )
                        Spacer(Modifier.height(4.dp))
                        // 清除按钮：小号文字按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                text = "清除",
                                onClick = onClear,
                                textStyle = TextStyle(fontSize = 12.sp),
                                colors = ButtonDefaults.textButtonColors(
                                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                ),
                            )
                        }
                    }
                    FilterPane.Date -> {
                        PickerHeader(title = "选择日期", onBack = { pane = FilterPane.Main })
                        Spacer(Modifier.height(2.dp))
                        NumberPicker(
                            value = dateOptions.indexOf(draftDate).coerceAtLeast(0),
                            onValueChange = { draftDate = dateOptions[it] },
                            range = 0..dateOptions.lastIndex,
                            label = { dateOptions[it] ?: "全部" },
                            textStyle = MiuixTheme.textStyles.body1,
                            itemHeight = 30.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = "清除",
                                onClick = {
                                    draftDate = null
                                    pane = FilterPane.Main
                                },
                            )
                            Button(
                                onClick = {
                                    onDateSelect(draftDate)
                                    pane = FilterPane.Main
                                },
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.primary,
                                    contentColor = MiuixTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("确定", fontSize = 13.sp)
                            }
                        }
                    }
                    FilterPane.Subject -> {
                        PickerHeader(title = "选择学科", onBack = { pane = FilterPane.Main })
                        Spacer(Modifier.height(2.dp))
                        NumberPicker(
                            value = subjectOptions.indexOf(draftSubject).coerceAtLeast(0),
                            onValueChange = { draftSubject = subjectOptions[it] },
                            range = 0..subjectOptions.lastIndex,
                            label = { subjectOptions[it] ?: "全部" },
                            textStyle = MiuixTheme.textStyles.body1,
                            itemHeight = 30.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = "清除",
                                onClick = {
                                    draftSubject = null
                                    pane = FilterPane.Main
                                },
                            )
                            Button(
                                onClick = {
                                    onSubjectSelect(draftSubject)
                                    pane = FilterPane.Main
                                },
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.primary,
                                    contentColor = MiuixTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("确定", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "返回",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.rotate(180f),
            )
        }
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FilterOptionRow(label: String, value: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

// ── 一键刷今日 ──────────────────────────────────────────────────

/** 一键刷今日入口卡片（刷卷中可暂停/继续） */
@Composable
private fun BrushTodayEntry(
    brushing: Boolean,
    paused: Boolean,
    progress: String,
    onClick: () -> Unit,
    onTogglePause: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        brushing && paused -> "已暂停（一键刷今日）"
                        brushing -> "正在刷卷…"
                        else -> "一键刷今日试卷"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (brushing) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        brushing && paused -> "点击右侧继续"
                        brushing && progress.isNotBlank() -> progress
                        brushing -> "正在初始化…"
                        else -> "选择日期，批量获取答案并提交交卷自批"
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (brushing) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onTogglePause,
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(if (paused) "继续" else "暂停", fontSize = 13.sp)
                }
            } else {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = "刷今日试卷",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

/** 刷今日：日期滚轮选择对话框（蓝色开始在上、白色取消在下） */
@Composable
private fun BrushDateDialog(
    dateOptions: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, dateOptions.lastIndex)) }
    WindowDialog(
        show = true,
        title = "选择要刷的日期",
        summary = "",
        onDismissRequest = onDismiss,
    ) {
        Column {
            NumberPicker(
                value = index,
                onValueChange = { index = it },
                range = 0..dateOptions.lastIndex,
                label = { dateOptions[it] },
                textStyle = MiuixTheme.textStyles.body1,
                itemHeight = 30.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            // 蓝色开始在上、白色取消在下（等高）
            Button(
                onClick = { onStart(dateOptions[index]) },
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("开始", fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
        }
    }
}

// ── 其他卡片 / 工具 ────────────────────────────────────────────

@Composable
private fun PaperRow(
    paper: Paper,
    count: Int?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = paper.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                val countText = count?.takeIf { it > 0 }?.let { "共 $it 题" }
                    ?: paper.questionCount.takeIf { it != "?" }?.let { "共 $it 题" }
                    ?: ""
                val meta = listOfNotNull(
                    paper.homeworkTitle.takeIf { it.isNotBlank() && it != "通过链接导入" },
                    paper.subjectName.takeIf { it.isNotBlank() },
                    countText,
                ).joinToString(" · ")
                Text(
                    text = meta.ifBlank { "打开查看详情" },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = "打开试卷",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

/** 粘贴链接查询入口卡片：点击进入链接查询页 */
@Composable
private fun LinkQueryEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "粘贴链接查询",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "输入 EWT360 试题链接（试卷 / 课后习题均可），直接查询",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "粘贴链接查询",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

// ── 日期工具 ───────────────────────────────────────────────────

private fun formatToday(): String {
    val d = java.util.Date()
    return String.format("%02d-%02d", d.month + 1, d.date)
}

/** 最近 count 天的 "MM-dd" 列表（今天在前） */
private fun recentDays(count: Int): List<String> {
    val cal = java.util.Calendar.getInstance()
    return (0 until count).map { i ->
        val c = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, -i) }
        String.format("%02d-%02d", c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
