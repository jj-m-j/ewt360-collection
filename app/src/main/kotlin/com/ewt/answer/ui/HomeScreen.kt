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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.HomeworkGroup
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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

@Composable
fun HomeScreen(
    userInfo: UserInfo?,
    paperCounts: Map<String, Int>,
    listState: LazyListState,
    onOpenPaper: (Paper) -> Unit,
    onOpenLinkQuery: () -> Unit,
    onOpenDebug: () -> Unit,
    onLogout: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val uiState by vm.uiState.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val dateFilter by vm.dateFilter.collectAsState()
    val subjectFilter by vm.subjectFilter.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    val searchState = rememberTextFieldState()
    // 外部状态 → 输入框（防止状态重置）
    LaunchedEffect(searchQuery) {
        if (searchState.text.toString() != searchQuery) {
            searchState.edit { replace(0, length, searchQuery) }
        }
    }
    // 输入框 → 外部状态（miuix TextField 为 state 驱动，无 onValueChange）
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }
            .distinctUntilChanged()
            .collect { vm.setSearchQuery(it) }
    }

    LaunchedEffect(Unit) { vm.load() }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "试卷列表",
                subtitle = userInfo?.realName?.let { "你好，$it" } ?: "",
                navigationIcon = {
                    TextButton(text = "退出登录", onClick = onLogout)
                },
                actions = {
                    TextButton(text = "调试模式", onClick = onOpenDebug)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { vm.load(force = true) },
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            // ── 筛选计算（Composable 上下文，勿移入 LazyColumn） ──
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
            // 搜索过滤 + 按日期分组（默认按日期倒序展示）
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

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "link") {
                    LinkQueryEntry(onClick = onOpenLinkQuery)
                }
                item(key = "search") {
                    TextField(
                        state = searchState,
                        label = "搜索试卷 / 课后习题",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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
                        if (dates.isNotEmpty()) {
                            item(key = "date_filter") {
                                FilterRow(
                                    label = "日期",
                                    options = dates,
                                    selected = dateFilter,
                                    onSelect = { vm.setDateFilter(it) },
                                )
                            }
                        }
                        if (subjects.isNotEmpty()) {
                            item(key = "subject_filter") {
                                FilterRow(
                                    label = "学科",
                                    options = subjects,
                                    selected = subjectFilter,
                                    onSelect = { vm.setSubjectFilter(it) },
                                )
                            }
                        }

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

/** 筛选行：标签 + 选项 chips（横向滚动） */
@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(end = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(text = "全部", selected = selected == null) { onSelect(null) } }
            items(options) { opt ->
                FilterChip(text = opt, selected = selected == opt) { onSelect(opt) }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun HomeworkHeader(group: HomeworkGroup) {
    SmallTitle(
        text = if (group.homework.title.isBlank()) {
            "作业 #${group.homework.homeworkId}"
        } else {
            group.homework.title
        },
    )
}

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
                // 题数：优先使用打开试卷后回传的真实题数，其次扫描字段
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
