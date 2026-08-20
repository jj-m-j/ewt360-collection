package com.ewt.answer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.Paper
import com.ewt.answer.data.QuestionItem
import com.ewt.answer.ui.components.AnswerDetailCard
import com.ewt.answer.ui.components.QuestionStatusBadge
import com.ewt.answer.ui.components.questionTypeTag
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun QuestionsScreen(
    paper: Paper,
    onBack: () -> Unit,
    onPaperOpened: (String, Int) -> Unit = { _, _ -> },
) {
    val vm: QuestionsViewModel = viewModel(key = "questions_${paper.paperId}", factory = QuestionsViewModel.factory(paper))
    val uiState by vm.uiState.collectAsState()
    val answers by vm.answers.collectAsState()
    val failed by vm.failed.collectAsState()
    val fetching by vm.fetching.collectAsState()
    val done by vm.done.collectAsState()
    val submitting by vm.submitting.collectAsState()
    val submitResult by vm.submitResult.collectAsState()

    var showSubmitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(uiState) {
        val s = (uiState as? QuestionsViewModel.UiState.Ready)?.session ?: return@LaunchedEffect
        if (s.questionCount > 0) onPaperOpened(s.paperId, s.questionCount)
    }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = paper.title,
                subtitle = when (val s = uiState) {
                    is QuestionsViewModel.UiState.Ready -> "共 ${s.questions.size} 道题"
                    else -> "扫描中…"
                },
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (val state = uiState) {
            QuestionsViewModel.UiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is QuestionsViewModel.UiState.Error -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.message,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(text = "重试", onClick = { vm.load() })
                }
            }
            is QuestionsViewModel.UiState.Ready -> {
                val questions = state.questions
                val grouped = remember(questions) {
                    questions.groupBy { it.groupName.ifBlank { "题目" } }
                }
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
                    item(key = "fetch_header") {
                        FetchHeaderCard(
                            total = questions.size,
                            done = done,
                            fetching = fetching,
                            failedCount = failed.size,
                            allFetched = done == questions.size && failed.isEmpty() && done > 0,
                            submitting = submitting,
                            submitResult = submitResult,
                            onFetchAll = { vm.fetchAllAnswers() },
                            onRetryFailed = { vm.retryFailed() },
                            onRequestSubmit = { showSubmitDialog = true },
                        )
                    }

                    grouped.forEach { (groupName, groupQuestions) ->
                        item(key = "group_$groupName") {
                            SmallTitle(text = groupName)
                        }
                        itemsIndexed(
                            groupQuestions,
                            key = { _, q -> q.questionId },
                        ) { _, q ->
                            QuestionRow(
                                question = q,
                                answer = answers[q.questionId],
                                failed = q.questionId in failed,
                                onRetry = { vm.retryOne(q) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 提交确认对话框（取消 + 小米蓝提交）
    if (showSubmitDialog) {
        WindowDialog(
            show = true,
            title = "提交答案",
            summary = "提交后试卷将标记为已交卷",
            onDismissRequest = { showSubmitDialog = false },
        ) {
            Column {
                Text(
                    text = "将提交全部已获取的选择题标准答案（系统阅卷）与非选择题（满分自批），随后交卷并自批。确定继续？",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showSubmitDialog = false },
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            showSubmitDialog = false
                            vm.submitAnswers()
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("提交", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FetchHeaderCard(
    total: Int,
    done: Int,
    fetching: Boolean,
    failedCount: Int,
    allFetched: Boolean,
    submitting: Boolean,
    submitResult: String?,
    onFetchAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onRequestSubmit: () -> Unit,
) {
    // 进度平滑过渡：done 跳变不再“卡顿”，400ms 缓动跟进
    val animatedProgress by animateFloatAsState(
        targetValue = if (total > 0) done.toFloat() / total else 0f,
        animationSpec = tween(400),
        label = "fetch_progress",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column {
            Button(
                onClick = onFetchAll,
                enabled = !fetching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        fetching -> "正在获取答案…"
                        allFetched -> "重新获取答案"
                        done > 0 -> "继续获取答案"
                        else -> "获取全部答案"
                    },
                    fontSize = 14.sp,
                )
            }

            if (fetching) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "正在获取答案  $done / $total",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            if (failedCount > 0 && !fetching) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$failedCount 道题获取失败",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(text = "重试失败", onClick = onRetryFailed)
                }
            }

            if (allFetched) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "✓ 全部获取完成",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            if (done > 0 && !fetching) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRequestSubmit,
                    enabled = !submitting && done > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (submitting) "正在提交…" else "提交答案",
                        fontSize = 14.sp,
                    )
                }
            }

            if (submitResult != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = submitResult,
                    fontSize = 12.sp,
                    color = if (submitResult.startsWith("提交失败")) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "点击题目行可展开查看答案、解析与知识点；提交前请确认已获取全部答案。",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun QuestionRow(
    question: QuestionItem,
    answer: com.ewt.answer.data.QuestionAnswer?,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "arrow_rotation",
    )

    val onClick: (() -> Unit)? = if (answer != null || failed) {
        { expanded = !expanded }
    } else {
        null
    }

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
                        text = "第 ${question.questionNumber} 题",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = questionTypeTag(question),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                QuestionStatusBadge(hasAnswer = answer != null, failed = failed)
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Spacer(Modifier.height(8.dp))
                AnswerDetailCard(
                    answer = answer,
                    failed = failed,
                    onRetry = onRetry,
                )
            }
        }
    }
}
