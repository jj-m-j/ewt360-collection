package com.ewt.answer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.DebugLog
import com.ewt.answer.data.HomeworkGroup
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun HomeScreen(
    userInfo: UserInfo?,
    onOpenPaper: (Paper) -> Unit,
    onOpenLinkQuery: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val uiState by vm.uiState.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val dateFilter by vm.dateFilter.collectAsState()
    val subjectFilter by vm.subjectFilter.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "试卷列表",
                subtitle = userInfo?.realName?.let { "你好，$it" } ?: "",
                navigationIcon = {
                    TextButton(text = "刷新", onClick = { vm.load(force = true) })
                },
                actions = {
                    TextButton(text = "关于", onClick = { showAboutDialog = true })
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
            LazyColumn(
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
                            EmptyHint("暂未找到可查询的任务\n请确认作业已布置试卷或课后习题")
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
                        val allPapers = remember(state.groups) { state.groups.flatMap { it.papers } }
                        val dates = remember(allPapers) {
                            allPapers.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }
                                .distinct().sortedDescending()
                        }
                        val subjects = remember(allPapers) {
                            allPapers.mapNotNull { it.subjectName.takeIf { s -> s.isNotBlank() } }
                                .distinct().sorted()
                        }

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

                        val filteredGroups = remember(state.groups, dateFilter, subjectFilter) {
                            state.groups.mapNotNull { g ->
                                val fp = g.papers.filter { p ->
                                    (dateFilter == null || p.date == dateFilter) &&
                                        (subjectFilter == null || p.subjectName == subjectFilter)
                                }
                                if (fp.isEmpty()) null else g.copy(papers = fp)
                            }
                        }

                        if (filteredGroups.isEmpty()) {
                            item(key = "filtered_empty") {
                                EmptyHint("当前筛选条件下没有任务")
                            }
                        } else {
                            filteredGroups.forEach { group ->
                                item(key = "group_${group.homework.homeworkId}") {
                                    HomeworkHeader(group)
                                }
                                items(group.papers, key = { it.paperId }) { paper ->
                                    PaperRow(paper = paper, onClick = { onOpenPaper(paper) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
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
private fun PaperRow(paper: Paper, onClick: () -> Unit) {
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
                val meta = listOfNotNull(
                    paper.date.takeIf { it.isNotBlank() },
                    paper.subjectName.takeIf { it.isNotBlank() },
                    "共 ${paper.questionCount} 题",
                ).joinToString(" · ")
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                    text = "输入 EWT360 试题链接，直接查询对应试卷",
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

/** 关于 / 调试面板：版本 + 日志 + 字体下载（WindowDialog 不依赖 Scaffold，保证可显示） */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLog by remember { mutableStateOf(false) }
    var fontLoading by remember { mutableStateOf(false) }

    WindowDialog(
        show = true,
        title = "关于",
        summary = "EWT360 答案查询 v1.0.0",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Text(
                text = "调试模式已开启，日志保存在本地 filesDir/logs/app.log",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))
            if (showLog) {
                val log = DebugLog.readLog()
                Text(
                    text = log.takeLast(3000).ifBlank { "(暂无日志)" },
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = if (showLog) "收起日志" else "查看日志",
                    onClick = { showLog = !showLog },
                )
                Spacer(Modifier.size(4.dp))
                TextButton(
                    text = if (fontLoading) "下载中…" else "下载字体",
                    enabled = !fontLoading,
                    onClick = {
                        fontLoading = true
                        scope.launch {
                            val ok = MiuixFonts.loadMiSans(context) != null
                            Toast.makeText(
                                context,
                                if (ok) "字体下载成功" else "字体下载失败",
                                Toast.LENGTH_SHORT,
                            ).show()
                            fontLoading = false
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "分享日志", onClick = { shareLog(context) })
                Spacer(Modifier.size(4.dp))
                TextButton(text = "清空日志", onClick = { DebugLog.clear() })
                Spacer(Modifier.size(4.dp))
                TextButton(text = "知道了", onClick = onDismiss)
            }
        }
    }
}

private fun shareLog(context: android.content.Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EWT360 调试日志")
            putExtra(Intent.EXTRA_TEXT, DebugLog.readLog().takeLast(20000))
        }
        context.startActivity(Intent.createChooser(intent, "分享日志"))
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
