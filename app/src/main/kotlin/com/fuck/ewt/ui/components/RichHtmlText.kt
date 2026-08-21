package com.fuck.ewt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fuck.ewt.data.HtmlCleaner
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渲染解析 HTML：文本 + 公式图 / 插图。
 * 公式图作为【流内元素】与文字混排（FlowRow），跟在文字后自动换行、同行显示，
 * 不再每个公式独占一行居中放大。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichHtmlText(
    html: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    textColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    val segments = remember(html) { HtmlCleaner.parseSegments(html) }
    if (segments.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        maxItemsInEachRow = Int.MAX_VALUE,
    ) {
        segments.forEach { seg ->
            when (seg) {
                is HtmlCleaner.Segment.Text -> {
                    Text(
                        text = seg.content,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.5f).sp,
                        color = textColor,
                    )
                }
                is HtmlCleaner.Segment.Image -> {
                    // 公式/行内图：高度贴近文字行，宽度按比例，充当流内元素
                    AsyncImage(
                        model = normalizeEwtImageUrl(seg.url),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .height((fontSize.value * 1.4f).dp)
                            .widthIn(max = 260.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/** 渲染附件图片列表（独立大图，占满宽） */
@Composable
fun AttachmentImageList(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    Column(modifier = modifier) {
        urls.forEach { url ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = normalizeEwtImageUrl(url),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/** 规范化图片 URL：JSON 转义反斜杠 / 相对协议 / http→https */
private fun normalizeEwtImageUrl(url: String): String {
    var u = url.trim().replace("\\/", "/")
    if (u.startsWith("//")) u = "https:$u"
    else if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
    return u
}
