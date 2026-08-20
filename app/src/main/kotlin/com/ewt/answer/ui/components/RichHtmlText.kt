package com.ewt.answer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ewt.answer.data.HtmlCleaner
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 渲染解析 HTML：文本 + 公式图 / 插图分段展示。
 * 图片仅允许 file.ewt360.com 域名（与油猴脚本一致），并补齐 UA / Referer 请求头避免 403。
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
 * EWT 图片：统一 https + 转义清理 + UA/Referer 请求头（部分图床无 Referer 会 403，导致图片无法显示）。
 */
@Composable
private fun EwtImage(url: String) {
    val context = LocalContext.current
    val model = remember(url) {
        val u = normalizeEwtImageUrl(url)
        ImageRequest.Builder(context)
            .data(u)
            .setHeader("User-Agent", "Mozilla/5.0")
            .setHeader("Referer", "https://web.ewt360.com/mystudy/")
            .setHeader("Origin", "https://web.ewt360.com")
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentScale = ContentScale.Fit,
    )
}

/** 规范化图片 URL：JSON 转义反斜杠 / 相对协议 / http→https */
private fun normalizeEwtImageUrl(url: String): String {
    var u = url.trim().replace("\\/", "/")
    if (u.startsWith("//")) u = "https:$u"
    else if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
    return u
}
