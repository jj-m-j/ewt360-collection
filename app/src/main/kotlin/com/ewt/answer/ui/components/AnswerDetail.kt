package com.ewt.answer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewt.answer.data.QuestionAnswer
import com.ewt.answer.data.QuestionItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 单题答案详情（展开态） */
@Composable
fun AnswerDetailCard(
    answer: QuestionAnswer?,
    failed: Boolean,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        insideMargin = PaddingValues(14.dp),
    ) {
        if (failed && answer == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "获取失败",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (onRetry != null) {
                    TextButton(text = "重试", onClick = onRetry)
                }
            }
        }
        answer?.let { a ->
            // 答案
            SectionLabel("答案")
            Spacer(Modifier.height(4.dp))
            if (a.answer.isNotBlank()) {
                Text(
                    text = a.answer,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "未返回标准答案",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 知识点
            Spacer(Modifier.height(12.dp))
            SectionLabel("知识点")
            Spacer(Modifier.height(4.dp))
            if (a.knowledges.isNotEmpty()) {
                Text(
                    text = a.knowledges.joinToString("、"),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            } else {
                Text(
                    text = "暂无",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 解析（始终显示）
            Spacer(Modifier.height(12.dp))
            SectionLabel("解析")
            Spacer(Modifier.height(4.dp))
            if (a.analysisHtml.isNotBlank()) {
                RichHtmlText(html = a.analysisHtml)
            } else {
                Text(
                    text = "暂无解析内容",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 附件图片
            if (a.attachmentImages.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("图片")
                AttachmentImageList(a.attachmentImages)
            }

            // 调试：答案与解析均未提取到时，展示接口原始返回
            if (a.answer.isBlank() && a.analysisHtml.isBlank() && a.rawJson.isNotBlank()) {
                var showRaw by remember { mutableStateOf(false) }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = if (showRaw) "收起原始返回" else "查看接口原始返回",
                    onClick = { showRaw = !showRaw },
                )
                AnimatedVisibility(visible = showRaw) {
                    Text(
                        text = a.rawJson,
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 题目标题辅助：拼接题型标签 */
fun questionTypeTag(q: QuestionItem): String {
    val parts = mutableListOf<String>()
    if (q.groupName.isNotBlank()) parts.add(q.groupName)
    if (q.questionType.isNotBlank()) parts.add(q.questionType)
    parts.add(if (q.subjective) "主观题" else "客观题")
    return parts.joinToString(" · ")
}

/** 状态指示：小圆点 + 文字（已获取 / 失败 / 未获取） */
@Composable
fun QuestionStatusBadge(
    hasAnswer: Boolean,
    failed: Boolean,
) {
    val (dotColor, text, textColor) = when {
        failed -> Triple(
            MiuixTheme.colorScheme.error,
            "失败",
            MiuixTheme.colorScheme.error,
        )
        hasAnswer -> Triple(
            MiuixTheme.colorScheme.primary,
            "已获取",
            MiuixTheme.colorScheme.primary,
        )
        else -> Triple(
            MiuixTheme.colorScheme.onSurfaceVariantActions,
            "未获取",
            MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = textColor,
        )
    }
}
