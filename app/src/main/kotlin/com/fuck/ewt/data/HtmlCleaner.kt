package com.fuck.ewt.data

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
    // 匹配所有图片：属性顺序任意（class/style/role/src 乱序均可），file.ewt360.com / Wiris 公式图 / 协议相对 // 均覆盖
    private val imgRe = Regex(
        """<img[^>]*?src="((?:https?:)?//[^"]*)"[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val newlineRe = Regex("""\n{3,}""")

    /** 图片 URL 统一：协议相对补 https，http 明文转 https（Android 默认禁明文） */
    private fun normalizeImgUrl(url: String): String {
        var u = url.trim()
        if (u.startsWith("//")) u = "https:$u"
        if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
        return u
    }

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

    /** 判断清洗后文本是否包含图片标签（用于答案区选择纯文本 / 富文本渲染） */
    fun containsImage(cleaned: String): Boolean =
        imgRe.containsMatchIn(cleaned)

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
        // 数学/符号实体（答案解析里常见，缺失会原样显示成 &xxx;）
        t = t.replace("&there4;", "\u2234")   // ∴ 所以
        t = t.replace("&because;", "\u2235")  // ∵ 因为
        t = t.replace("&ne;", "\u2260")       // ≠
        t = t.replace("&le;", "\u2264")       // ≤
        t = t.replace("&ge;", "\u2265")       // ≥
        t = t.replace("&plusmn;", "\u00B1")   // ±
        t = t.replace("&minus;", "\u2212")    // −
        t = t.replace("&times;", "\u00D7")    // ×
        t = t.replace("&divide;", "\u00F7")   // ÷
        t = t.replace("&radic;", "\u221A")    // √
        t = t.replace("&sum;", "\u2211")      // ∑
        t = t.replace("&infin;", "\u221E")    // ∞
        t = t.replace("&deg;", "\u00B0")      // °
        t = t.replace("&Prime;", "\u2033")    // ″
        t = t.replace("&prime;", "\u2032")    // ′
        t = t.replace("&ang;", "\u2220")      // ∠
        t = t.replace("&Delta;", "\u0394")    // Δ
        t = t.replace("&pi;", "\u03C0")       // π
        t = t.replace("&alpha;", "\u03B1")    // α
        t = t.replace("&beta;", "\u03B2")     // β
        t = t.replace("&gamma;", "\u03B3")    // γ
        t = t.replace("&theta;", "\u03B8")    // θ
        t = t.replace("&lambda;", "\u03BB")   // λ
        t = t.replace("&mu;", "\u03BC")       // μ
        t = t.replace("&perp;", "\u22A5")     // ⊥
        t = t.replace("&parallel;", "\u2225") // ∥
        t = t.replace("&sim;", "\u223C")      // ∼
        t = t.replace("&rarr;", "\u2192")     // →
        t = t.replace("&larr;", "\u2190")     // ←
        t = t.replace("&harr;", "\u2194")     // ↔
        t = t.replace("&in;", "\u2208")       // ∈
        t = t.replace("&cup;", "\u222A")      // ∪
        t = t.replace("&cap;", "\u2229")      // ∩
        t = t.replace("&empty;", "\u2205")    // ∅
        t = t.replace("&forall;", "\u2200")   // ∀
        t = t.replace("&exist;", "\u2203")    // ∃
        t = t.replace("&prop;", "\u221D")     // ∝
        t = t.replace("&infin;", "\u221E")    // (已在上方)
        return t
    }
}
