package com.fuck.ewt.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.fuck.ewt.data.HtmlCleaner
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渲染解析 HTML：文本 + 公式图 / 插图分段展示。
 * 图片走全局 ImageLoader（EwtApplication 已配 UA/Referer 头），并统一 https + 转义清理。
 * Wiris 公式 SVG：按自身宽高比缩放，高度限 [min,max] 区间（分数等结构自然更高但封顶）。
 */
@Composable
fun RichHtmlText(
    html: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    textColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    val segments = remember(html) { HtmlCleaner.parseSegments(html) }
    if (segments.isEmpty()) return
    Column(modifier = modifier) {
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
                    EwtImage(url = seg.url)
                }
            }
        }
    }
}

/** 渲染附件图片列表 */
@Composable
fun AttachmentImageList(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    Column(modifier = modifier) {
        urls.forEach { url ->
            EwtImage(url = url)
        }
    }
}

/**
 * EWT 图片。
 * Wiris 公式 SVG：按自身宽高比缩放，高度限 14..48dp 区间，宽度自适应（max 300dp）。
 * 普通插图：fillMaxWidth 自适应。
 */
@Composable
private fun EwtImage(url: String) {
    val normalized = normalizeEwtImageUrl(url)
    val isFormula = normalized.contains("Wirisformula") || normalized.contains("wiris")
    if (isFormula) {
        // 根据加载到的 intrinsic 尺寸计算显示高度，避免被 SVG 超大 viewBox 拉伸
        var displayHeight by remember(normalized) { mutableStateOf<Dp?>(null) }
        AsyncImage(
            model = normalized,
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .height(displayHeight ?: 24.dp)
                .widthIn(max = 300.dp),
            contentScale = ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val s = state.painter.intrinsicSize
                    if (s.width > 0f && s.height > 0f && !s.width.isNaN() && !s.height.isNaN()) {
                        // 目标高度：以文字高度为基准，按宽高比给分数这类高结构留更多空间
                        val ratio = s.width / s.height
                        val h = when {
                            ratio > 3f -> 22.dp      // 很宽的公式，矮一点
                            ratio > 1.2f -> 30.dp    // 常规
                            else -> 48.dp            // 分数等高瘦结构，给足高度
                        }
                        displayHeight = h.coerceIn(14.dp, 48.dp)
                    }
                }
            },
        )
    } else {
        AsyncImage(
            model = normalized,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** 规范化图片 URL：JSON 转义反斜杠 / 相对协议 / http→https */
private fun normalizeEwtImageUrl(url: String): String {
    var u = url.trim().replace("\\/", "/")
    if (u.startsWith("//")) u = "https:$u"
    else if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
    return u
}
