package com.ewt.answer.data

/**
 * HTML 清洗工具 —— 移植自 ewt-getanwser.js
 *
 * 思路与油猴脚本一致：
 * 1. 保留公式图（Wirisformula <img>）与 file.ewt360.com 图片
 * 2. <br> 转为换行
 * 3. 去掉除 img 以外的所有标签
 * 4. 还原常见 HTML 实体
 * 5. 折叠连续换行
 */
object HtmlCleaner {

    sealed class Segment {
        data class Text(val content: String) : Segment()
        data class Image(val url: String) : Segment()
    }

    private val wirisRe =
        Regex("""<img[^>]*Wirisformula[^>]*src="([^"]*)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val brRe = Regex("""<br[^>]*>""", RegexOption.IGNORE_CASE)
    private val stripTagRe = Regex("""<(?!img\b|/img\b)[^>]+>""", RegexOption.IGNORE_CASE)
    private val imgRe = Regex(
        """<img\s+src="(https?://file\.ewt360\.com/[^"]*)"(?:\s+style="([^"]*)")?\s*/?>""",
        RegexOption.IGNORE_CASE,
    )
    private val newlineRe = Regex("""\n{3,}""")

    /** 图片 URL 统一转 https（http 明文在 Android 默认被禁，Coil 无法加载） */
    private fun normalizeImgUrl(url: String): String =
        url.replace(Regex("""^http://file\.ewt360\.com/"""), "https://file.ewt360.com/")

    /**
     * 预处理：清洗为“仅含 <img> 标签”的安全 HTML 字符串（与 JS cleanHtmlKeepImg 一致）
     */
    fun preprocess(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var t = html
        // 部分接口返回的转义反斜杠（JS 中展示前手动还原）
        t = t.replace("\\<", "<").replace("\\:", ":")
        t = wirisRe.replace(t) { "<img src=\"${it.groupValues[1]}\" />" }
        t = brRe.replace(t, "\n")
        t = stripTagRe.replace(t, "")
        t = decodeEntities(t)
        t = newlineRe.replace(t, "\n\n")
        return t.trim()
    }

    /** 纯文本清洗（用于答案文本等） */
    fun clean(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var t = text
        t = t.replace("\\<", "<").replace("\\:", ":")
        t = wirisRe.replace(t) { "<img src=\"${it.groupValues[1]}\" />" }
        t = brRe.replace(t, "\n")
        t = stripTagRe.replace(t, "")
        t = decodeEntities(t)
        t = newlineRe.replace(t, "\n\n")
        return t.trim()
    }

    /** 解析为文本 + 图片分段（用于 Compose 渲染解析内容） */
    fun parseSegments(html: String?): List<Segment> {
        val t = preprocess(html)
        if (t.isEmpty()) return emptyList()
        val segments = mutableListOf<Segment>()
        var last = 0
        for (m in imgRe.findAll(t)) {
            if (m.range.first > last) {
                val chunk = t.substring(last, m.range.first).trim()
                if (chunk.isNotEmpty()) segments.add(Segment.Text(chunk))
            }
            segments.add(Segment.Image(normalizeImgUrl(m.groupValues[1])))
            last = m.range.last + 1
        }
        if (last < t.length) {
            val chunk = t.substring(last).trim()
            if (chunk.isNotEmpty()) segments.add(Segment.Text(chunk))
        }
        return segments
    }

    /** 提取选择题答案字母（如 ["A", "C"]） */
    fun extractChoiceAnswers(rightAnswer: List<String>?): List<String> {
        if (rightAnswer.isNullOrEmpty()) return emptyList()
        return rightAnswer.map { it.trim() }.filter { Regex("^[A-Z]+$").matches(it) }
    }

    /**
     * 格式化 rightAnswer 为展示文本（与 JS 逻辑一致）：
     * - 选择题：A、C → "A, C"
     * - 填空题等：逐个清洗后换行拼接
     * - 空：返回空串（UI 显示“主观题”占位）
     */
    fun formatRightAnswer(rightAnswer: List<String>?): String {
        val opts = extractChoiceAnswers(rightAnswer)
        if (opts.isNotEmpty()) {
            return opts.map { it.toCharArray().joinToString(", ") }.joinToString("  |  ")
        }
        val rest = rightAnswer?.filter { it.isNotBlank() } ?: emptyList()
        if (rest.isNotEmpty()) return rest.joinToString("\n") { clean(it) }
        return ""
    }

    private fun decodeEntities(s: String): String {
        var t = s
        t = t.replace("&ldquo;", "\u201C").replace("&rdquo;", "\u201D")
        t = t.replace("&lsquo;", "\u2018").replace("&rsquo;", "\u2019")
        t = t.replace("&nbsp;", " ")
        t = t.replace("&amp;", "&")
        t = t.replace("&lt;", "<").replace("&gt;", ">")
        t = t.replace("&#39;", "'").replace("&quot;", "\"")
        t = t.replace("&hellip;", "\u2026")
        return t
    }
}
