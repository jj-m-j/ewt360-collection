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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 筛选图标（三条横线，material filter_list） */
private val FilterListIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilterList",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes("M24,10H0V12H24V10ZM24,6H0V8H24V6ZM0,16H24V14H0V16Z"),
        fill = SolidColor(Color.Black),
    ).build()
}

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

    var showFilterPopup by remember { mutableStateOf(false) }
    var showBrushDialog by remember { mutableStateOf(false) }

    // 顶栏独立 backdrop：只捕获 Scaffold 内容区（列表），不含顶栏自身 —— 避免 RenderNode 循环引用崩溃
    val topBarBackdrop = rememberLayerBackdrop()
    val glassSurface = MiuixTheme.colorScheme.surface

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

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "试卷列表",
                subtitle = userInfo?.realName?.let { "你好，$it" } ?: "",
                titlePadding = 16.dp,
                // 液态玻璃顶栏：模糊内容层（Android 12+），低版本降级为半透明底色
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
            )
        },
    ) { padding ->
        // 内容区（不含顶栏）作为顶栏模糊的捕获源
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(topBarBackdrop),
        ) {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                // ── 固定区：搜索框及以上（下拉刷新不参与动效） ──
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LinkQueryEntry(onClick = onOpenLinkQuery)
                    BrushTodayEntry(
                        brushing = brushing,
                        progress = brushProgress,
                        onClick = { showBrushDialog = true },
                    )
                    TextField(
                        state = searchState,
                        label = "搜索试卷 / 课后习题",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── 筛选行：三条杠在搜索框下边靠右，弹窗锚定在按钮旁 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val conds = listOfNotNull(dateFilter, subjectFilter)
                    Text(
                        text = if (conds.isEmpty()) "全部任务" else "筛选：" + conds.joinToString(" · "),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { showFilterPopup = true }) {
                            Icon(
                                imageVector = FilterListIcon,
                                contentDescription = "筛选",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                        if (showFilterPopup) {
                            val density = LocalDensity.current
                            Popup(
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, with(density) { 6.dp.roundToPx() }),
                                onDismissRequest = { showFilterPopup = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                FilterPopupCard(
                                    dateFilter = dateFilter,
                                    subjectFilter = subjectFilter,
                                    dates = dates,
                                    subjects = subjects,
                                    onDateSelect = {
                                        vm.setDateFilter(it)
                                        showFilterPopup = false
                                    },
                                    onSubjectSelect = {
                                        vm.setSubjectFilter(it)
                                        showFilterPopup = false
                                    },
                                    onClear = {
                                        vm.setDateFilter(null)
                                        vm.setSubjectFilter(null)
                                    },
                                )
                            }
                        }
                    }
                }

                // ── 可下拉区：只有这里下拉刷新有动效 ──
                Box(Modifier.weight(1f)) {
                    PullToRefresh(
                        isRefreshing = refreshing,
                        onRefresh = { vm.load(force = true) },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 80.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when (val state = uiState) {
                                HomeViewModel.UiState.Loading -> {
                                    item(key = "loading") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 72.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
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
                                        dateGroups.forEach { (date, papers) ->
                                            item(key = "date_$date") {
                                                SmallTitle(
                                                    text = if (date == "其他") "未分类" else date,
                                                )
                                            }
                                            items(papers, key = { it.paperId }) { paper ->
                                                PaperRow(
                                                    paper = paper,
                                                    count = paperCounts[paper.paperId],
                                                    onClick = { onOpenPaper(paper) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { vm.clearBrushResult() },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("知道了", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── 筛选锚点弹窗（三条杠旁边） ───────────────────────────────────

private enum class FilterPane { Main, Date, Subject }

@Composable
private fun FilterPopupCard(
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

    // null = 全部
    val dateOptions = remember(dates) { listOf<String?>(null) + dates }
    val subjectOptions = remember(subjects) { listOf<String?>(null) + subjects }

    Card(
        modifier = Modifier.width(280.dp),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        when (pane) {
            FilterPane.Main -> {
                Column {
                    Text(
                        text = "筛选",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(text = "清除", onClick = onClear)
                    }
                }
            }
            FilterPane.Date -> {
                Column {
                    PickerHeader(title = "选择日期", onBack = { pane = FilterPane.Main })
                    NumberPicker(
                        value = dateOptions.indexOf(draftDate).coerceAtLeast(0),
                        onValueChange = { draftDate = dateOptions[it] },
                        range = 0..dateOptions.lastIndex,
                        label = { dateOptions[it] ?: "全部" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            text = "清除",
                            onClick = {
                                draftDate = null
                                pane = FilterPane.Main
                            },
                        )
                        Spacer(Modifier.width(10.dp))
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
                            Text("确定", fontSize = 14.sp)
                        }
                    }
                }
            }
            FilterPane.Subject -> {
                Column {
                    PickerHeader(title = "选择学科", onBack = { pane = FilterPane.Main })
                    NumberPicker(
                        value = subjectOptions.indexOf(draftSubject).coerceAtLeast(0),
                        onValueChange = { draftSubject = subjectOptions[it] },
                        range = 0..subjectOptions.lastIndex,
                        label = { subjectOptions[it] ?: "全部" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            text = "清除",
                            onClick = {
                                draftSubject = null
                                pane = FilterPane.Main
                            },
                        )
                        Spacer(Modifier.width(10.dp))
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
                            Text("确定", fontSize = 14.sp)
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
            fontSize = 15.sp,
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
            .padding(vertical = 3.dp),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

// ── 一键刷今日 ──────────────────────────────────────────────────

/** 一键刷今日入口卡片 */
@Composable
private fun BrushTodayEntry(brushing: Boolean, progress: String, onClick: () -> Unit) {
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
                    text = if (brushing) "正在刷卷…" else "一键刷今日试卷",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (brushing) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
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
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "刷今日试卷",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/** 刷今日：日期滚轮选择对话框 */
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
        title = "一键刷今日试卷",
        summary = "选择要刷的日期",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Text(
                text = "滚动选择日期，确认后开始批量刷卷（自动获取答案 → 提交交卷 → 自批）",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            NumberPicker(
                value = index,
                onValueChange = { index = it },
                range = 0..dateOptions.lastIndex,
                label = { dateOptions[it] },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { onStart(dateOptions[index]) },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("开始刷卷", fontSize = 14.sp)
                }
            }
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
            .padding(vertical = 3.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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
                // 课程信息（作业名） · 学科 · 题数
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
            .padding(vertical = 6.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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
            .padding(vertical = 80.dp),
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
