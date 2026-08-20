package com.ewt.answer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
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

/** 刷新图标（material refresh） */
private val RefreshIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes("M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"),
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

    var showBrushDialog by remember { mutableStateOf(false) }

    // 顶栏独立 backdrop：捕获 Scaffold 内容区（列表），材质与底部 LiquidGlass 同源（blur 10dp + surface 62%）
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

    // ── 滚动收缩进度：基于列表滚动距离连续插值（非阈值切换） ──
    // with(LocalDensity.current) { dp.toPx() } —— miuix Surface 同款写法（Dp.toPx 无参成员扩展）
    val collapseDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val collapseProgress by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            if (first.index > 0) 1f else (-first.offset / collapseDistance).coerceIn(0f, 1f)
        }
    }

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

    Scaffold(
        topBar = {
            // 自定义滚动收缩 Header：LiquidGlass 材质完全保留，只动容器尺寸与内部布局
            CollapsingHeader(
                progress = collapseProgress,
                userInfo = userInfo,
                backdrop = topBarBackdrop,
                glassSurface = glassSurface,
            )
        },
    ) { padding ->
        // 内容区（不含顶栏）作为顶栏模糊的捕获源
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(topBarBackdrop),
        ) {
            // 下拉刷新：指示器通过 contentPadding.top 明显下移，远离状态栏（源码 offset(y=contentPadding.top)）
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { vm.load(force = true) },
                contentPadding = PaddingValues(top = 56.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // ── 任务工具区：全部任务 + 刷新（同一 Section Header） ──
                    item(key = "toolbar") {
                        RefreshRow(
                            refreshing = refreshing,
                            dateFilter = dateFilter,
                            subjectFilter = subjectFilter,
                            onRefresh = { vm.load(force = true) },
                        )
                    }
                    // ── 搜索 / 筛选：搜索框 + 三条杠（同一行，弱化视觉权重） ──
                    item(key = "search") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextField(
                                state = searchState,
                                label = "搜索试卷 / 课后习题",
                                useLabelAsPlaceholder = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(2.dp))
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
                    // ── 快捷操作区 ──
                    item(key = "quick_title") {
                        SmallTitle(text = "快捷操作")
                    }
                    item(key = "link") {
                        LinkQueryEntry(onClick = onOpenLinkQuery)
                    }
                    item(key = "brush_today") {
                        BrushTodayEntry(
                            brushing = brushing,
                            progress = brushProgress,
                            onClick = { showBrushDialog = true },
                        )
                    }

                    when (val state = uiState) {
                        HomeViewModel.UiState.Loading -> {
                            item(key = "scan_status") {
                                ScanStatusRow(statusText.ifBlank { "正在扫描作业…" })
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
                                        .padding(vertical = 40.dp),
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

// ── 滚动收缩 Header（LiquidGlass 材质保留，容器与内部布局可动） ─────

@Composable
private fun CollapsingHeader(
    progress: Float,
    userInfo: UserInfo?,
    backdrop: LayerBackdrop,
    glassSurface: Color,
) {
    val headerHeight = lerp(92.dp, 52.dp, progress)
    val titleSize = lerp(26.sp, 17.sp, progress)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            // LiquidGlass 材质与底部同源：blur 10dp + surface 62%（完全保留）
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(10f.dp.toPx()) },
                onDrawSurface = {
                    drawRect(glassSurface.copy(alpha = 0.62f))
                },
            ),
    ) {
        // px 换算全部在 composable 作用域预计算（miuix 同款 with(density){dp.toPx()}），graphicsLayer 只引用 Float
        val containerW = with(density) { maxWidth.toPx() }
        val offset16Px = with(density) { 16.dp.toPx() }
        val offset8Px = with(density) { 8.dp.toPx() }
        var columnW by remember { mutableIntStateOf(0) }
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .onSizeChanged { columnW = it.width }
                    .graphicsLayer {
                        // progress=0 时标题区位于左侧 16dp；progress=1 时水平居中（连续插值）
                        translationX = (1f - progress) * -(containerW / 2f - offset16Px - columnW / 2f)
                    },
            ) {
                Text(
                    text = "试卷列表",
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(lerp(4.dp, 0.dp, progress)))
                // 问候语：随滚动淡出 + 上移 8dp + 轻微缩小（始终占位保证标题垂直稳定）
                Text(
                    text = userInfo?.realName?.let { "你好，$it" } ?: "",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - progress
                        translationY = -offset8Px * progress
                        scaleX = 1f - 0.04f * progress
                        scaleY = 1f - 0.04f * progress
                    },
                )
            }
        }
    }
}

// ── 任务工具区（全部任务 + 刷新） ────────────────────────────────

@Composable
private fun RefreshRow(
    refreshing: Boolean,
    dateFilter: String?,
    subjectFilter: String?,
    onRefresh: () -> Unit,
) {
    // 刷新时图标持续旋转（轻量状态反馈）
    val infinite = rememberInfiniteTransition(label = "refresh_spin")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "refresh_angle",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val conds = listOfNotNull(dateFilter, subjectFilter)
        Text(
            text = if (conds.isEmpty()) "全部任务" else "筛选：" + conds.joinToString(" · "),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = RefreshIcon,
                contentDescription = "刷新",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (refreshing) angle else 0f
                },
            )
        }
    }
}

// ── 搜索 / 筛选（三条杠锚定搜索框右侧，弱化视觉权重） ─────────────

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
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = FilterListIcon,
                contentDescription = "筛选",
                // 弱化：次级文字色，不再抢眼
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
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

/**
 * 三条杠弹层 —— Circle → Capsule → Dialog 连续 Morph（缩小版：固定 260×240，滚轮 3 项）
 */
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

    val morph = remember { Animatable(0f) }
    val contentProgress = remember { Animatable(0f) }
    val exitContent = remember { Animatable(0f) }
    // 预计算 px（graphicsLayer 内只引用 Float）
    val offset6Px = with(LocalDensity.current) { 6.dp.toPx() }

    val morphEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    LaunchedEffect(Unit) {
        if (!exiting) {
            morph.animateTo(1f, tween(300, easing = morphEase))
        }
    }
    LaunchedEffect(Unit) {
        if (!exiting) {
            delay(200)
            contentProgress.animateTo(1f, tween(150, easing = LinearOutSlowInEasing))
        }
    }
    LaunchedEffect(exiting) {
        if (exiting) {
            exitContent.animateTo(1f, tween(90, easing = LinearOutSlowInEasing))
            morph.animateTo(0f, tween(200, easing = FastOutLinearInEasing))
            onExitFinished()
        }
    }

    val p = morph.value
    // 缩小版：小圆(44×44) → 对话框(260×240)，圆角 22 → 20
    val w = lerp(44.dp, 260.dp, p)
    val h = lerp(44.dp, 240.dp, p)
    val corner = lerp(22.dp, 20.dp, p)
    val contentAlpha = (contentProgress.value * (1f - exitContent.value)).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(corner))
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0f)
                alpha = (0.75f + 0.25f * p).coerceIn(0f, 1f)
            },
        cornerRadius = corner,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = (1f - contentAlpha) * offset6Px
                },
        ) {
            AnimatedContent(
                targetState = pane,
                transitionSpec = {
                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(120)),
                        )
                },
                label = "filter_pane",
            ) { p2 ->
                Column(Modifier.fillMaxSize()) {
                    when (p2) {
                        FilterPane.Main -> {
                            Text(
                                text = "筛选",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
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
                            Spacer(Modifier.weight(1f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(text = "清除", onClick = onClear)
                            }
                        }
                        FilterPane.Date -> {
                            PickerHeader(title = "选择日期", onBack = { pane = FilterPane.Main })
                            Spacer(Modifier.height(4.dp))
                            NumberPicker(
                                value = dateOptions.indexOf(draftDate).coerceAtLeast(0),
                                onValueChange = { draftDate = dateOptions[it] },
                                range = 0..dateOptions.lastIndex,
                                label = { dateOptions[it] ?: "全部" },
                                visibleItemCount = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.weight(1f))
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
                        FilterPane.Subject -> {
                            PickerHeader(title = "选择学科", onBack = { pane = FilterPane.Main })
                            Spacer(Modifier.height(4.dp))
                            NumberPicker(
                                value = subjectOptions.indexOf(draftSubject).coerceAtLeast(0),
                                onValueChange = { draftSubject = subjectOptions[it] },
                                range = 0..subjectOptions.lastIndex,
                                label = { subjectOptions[it] ?: "全部" },
                                visibleItemCount = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.weight(1f))
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
    }
}

@Composable
private fun PickerHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
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
            .padding(vertical = 2.dp),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── 快捷操作（紧凑） ──────────────────────────────────────────

/** 一键刷今日入口卡片 */
@Composable
private fun BrushTodayEntry(brushing: Boolean, progress: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                if (brushing && progress.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = progress,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "刷今日试卷",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/** 粘贴链接查询入口卡片 */
@Composable
private fun LinkQueryEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "粘贴链接查询",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/** 扫描状态行（融入页面结构） */
@Composable
private fun ScanStatusRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
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
                    lineHeight = 21.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
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
