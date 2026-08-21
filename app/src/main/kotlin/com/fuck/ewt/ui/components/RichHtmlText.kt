package com.fuck.ewt.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.fuck.ewt.data.HtmlCleaner
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渲染解析 HTML：文本 + 行内公式图。
 * 公式图作为【行内元素】嵌入文字流（AnnotatedString + InlineContent），与文字同一行、
 * 按文字高度缩放，并垂直居中，模仿官方排版效果。
 */
@Composable
fun RichHtmlText(
    html: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    textColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    val segments = remember(html) { HtmlCleaner.parseSegments(html) }
    if (segments.isEmpty()) return

    // 记录每个图片的宽高比（加载后更新，用于计算行内占位宽度）
    val ratios = remember(segments) { mutableStateMapOf<Int, Float>() }
    val lineHeight = fontSize.value * 1.5f
    val imgHeight = fontSize.value * 1.35f

    // 行内内容：图片委托
    val inline = remember(segments, ratios) {
        val map = mutableMapOf<String, InlineTextContent>()
        segments.forEachIndexed { idx, seg ->
            if (seg is HtmlCleaner.Segment.Image) {
                val ratio = ratios[idx] ?: 2.2f
                map["IMG_$idx"] = InlineTextContent(
                    placeholder = Placeholder(
                        width = (imgHeight * ratio).sp,
                        height = imgHeight.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    AsyncImage(
                        model = normalizeEwtImageUrl(seg.url),
                        contentDescription = null,
                        modifier = Modifier
                            .height(imgHeight.dp)
                            .widthIn(max = 220.dp),
                        contentScale = ContentScale.Fit,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                val s = state.painter.intrinsicSize
                                if (s.width > 0f && s.height > 0f && !s.width.isNaN() && !s.height.isNaN()) {
                                    val r = s.width / s.height
                                    if (ratios[idx] != r) ratios[idx] = r
                                }
                            }
                        },
                    )
                }
            }
        }
        map
    }

    // 文本 + 图片锚点
    val annotated = remember(segments, ratios) {
        buildAnnotatedString {
            segments.forEachIndexed { idx, seg ->
                when (seg) {
                    is HtmlCleaner.Segment.Text -> append(seg.content)
                    is HtmlCleaner.Segment.Image -> appendInlineContent("IMG_$idx", "\uFFFC")
                }
            }
        }
    }

    androidx.compose.foundation.text.BasicText(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight.sp,
            color = textColor,
            fontWeight = fontWeight,
        ),
        inlineContent = inline,
    )
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
