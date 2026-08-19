package com.ewt.answer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewt.answer.data.QuestionAnswer
import com.ewt.answer.data.QuestionItem
import top.yukonga.miuix.kmp.basic.Badge
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
                    text = "（主观题，请在解析中查看参考答案）",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 知识点
            if (a.knowledges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("知识点")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = a.knowledges.joinToString("、"),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }

            // 解析
            if (a.analysisHtml.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("解析")
                Spacer(Modifier.height(4.dp))
                RichHtmlText(html = a.analysisHtml)
            }

            // 附件图片
            if (a.attachmentImages.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("图片")
                AttachmentImageList(a.attachmentImages)
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

/** 状态徽标 */
@Composable
fun QuestionStatusBadge(
    hasAnswer: Boolean,
    failed: Boolean,
) {
    val (container, content, text) = when {
        failed -> Triple(
            MiuixTheme.colorScheme.errorContainer,
            MiuixTheme.colorScheme.error,
            "失败",
        )
        hasAnswer -> Triple(
            MiuixTheme.colorScheme.primaryContainer,
            MiuixTheme.colorScheme.primary,
            "已获取",
        )
        else -> Triple(
            MiuixTheme.colorScheme.secondaryContainer,
            MiuixTheme.colorScheme.onSecondaryContainer,
            "未获取",
        )
    }
    Badge(
        containerColor = container,
        contentColor = content,
    ) {
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            color = content,
        )
        Spacer(Modifier.width(4.dp))
    }
}
