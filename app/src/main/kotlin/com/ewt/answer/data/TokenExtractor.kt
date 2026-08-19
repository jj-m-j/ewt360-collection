package com.ewt.answer.data

import android.webkit.CookieManager
import java.net.URLDecoder

/**
 * 从 WebView Cookie 中提取 EWT token。
 *
 * 与 ewt-getanwser.js 的 getCookie('token') 对应：
 * JS 从 document.cookie 读取 token，说明 token 存在于 EWT 域名的 Cookie 中。
 *
 * Android 侧说明：
 * - CookieManager.getCookie(url) 返回该 URL 匹配域名的全部 Cookie（包括 HttpOnly），
 *   与 JS 的 document.cookie（受 HttpOnly 限制）不同，因此比 JS 更可靠。
 * - 若 token 为 HttpOnly，JS 无法读取，但 CookieManager 仍可读取。
 * - 依次尝试 web.ewt360.com / ewt360.com / gateway.ewt360.com / sso.ewt360.com，
 *   覆盖 token 可能写入的不同子域。
 */
object TokenExtractor {

    private val TOKEN_RE = Regex("(?:^|;)\\s*token=([^;]+)")

    private val candidateUrls = listOf(
        "https://web.ewt360.com/",
        "https://www.ewt360.com/",
        "https://ewt360.com/",
        "https://gateway.ewt360.com/",
        "https://sso.ewt360.com/",
        "https://teacher.ewt360.com/",
    )

    /** 从 CookieManager 读取 token（Cookie 值可能被 URL 编码，与 JS decodeURIComponent 一致） */
    fun readFromCookieManager(): String? {
        for (url in candidateUrls) {
            val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                ?: continue
            val m = TOKEN_RE.find(cookies) ?: continue
            val raw = m.groupValues[1]
            return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        }
        return null
    }
}
