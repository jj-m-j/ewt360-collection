package com.ewt.answer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ewt.answer.data.HtmlCleaner
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渲染解析 HTML：文本 + 公式图 / 插图分段展示。
 * 图片走全局 ImageLoader（EwtApplication 已配 UA/Referer 头），并统一 https + 转义清理。
 * Wiris 公式 SVG 固定高度（行内公式大小），普通插图保持原尺寸自适应。
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
 * EWT 图片：https 规范化 + 全局 ImageLoader 头（UA/Referer）加载。
 * Wiris 公式 SVG：固定高度 28dp、宽度自适应（大小统一，不再拉伸）；普通插图：fillMaxWidth。
 */
@Composable
private fun EwtImage(url: String) {
    val normalized = normalizeEwtImageUrl(url)
    val isFormula = normalized.contains("Wirisformula") || normalized.contains("wiris")
    if (isFormula) {
        AsyncImage(
            model = normalized,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .height(28.dp),
            contentScale = ContentScale.Fit,
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
