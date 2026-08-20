package com.ewt.answer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ewt.answer.data.SecureTokenStore
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网页刷课页（WebView 独立页）：
 * 概览卡（进度/控制/URL复制）→ WebView（固定 60% 高度，真实浏览器环境）。
 * 加载前注入 app token 到 cookie（接续登录态）；默认 UA 与登录页一致；
 * 注入 JS 自动连播（85% 切换 + 2 倍速 + 自动过检 + 锁进度条 + 跳题），JS↔原生桥回传进度；
 * 拦截 ewt app 跳转/下载/下载引导页。
 */
@Composable
fun WebViewBrushScreen(
    onBack: () -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var brushing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("进入课程视频页后点「开始自动刷」") }
    var lessonTitle by remember { mutableStateOf("未开始") }
    var progress by remember { mutableIntStateOf(0) }
    var switchedCount by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = "网页刷课",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.rotate(180f),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 概览卡（固定高度）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(
                        text = if (brushing) "正在刷：$lessonTitle" else "当前：$lessonTitle",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (progress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$progress% · 2 倍速 · 已刷 $switchedCount 课",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                val wv = webViewRef ?: return@Button
                                brushing = !brushing
                                if (brushing) {
                                    wv.evaluateJavascript(BRUSH_JS, null)
                                    status = "自动刷运行中：85% 自动切换 · 2 倍速"
                                } else {
                                    wv.evaluateJavascript("window.__ewtBrushOn = false;", null)
                                    status = "已停止"
                                }
                            },
                            modifier = Modifier.width(130.dp),
                        ) {
                            Text(if (brushing) "停止" else "开始自动刷", fontSize = 14.sp)
                        }
                        Text(
                            text = status,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentUrl.ifBlank { "https://web.ewt360.com/ …" },
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            text = "复制",
                            onClick = { clipboard.setText(AnnotatedString(currentUrl)) },
                        )
                    }
                }
            }

            // WebView（固定 60% 高度，避免 weight+AndroidView 布局问题）
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
            ) {
                AndroidView(
                    factory = { ctx ->
                        createBrushWebView(
                            ctx,
                            onProgress = { title, pct, switched ->
                                lessonTitle = title
                                progress = pct
                                switchedCount = switched
                            },
                            onUrl = { url -> currentUrl = url },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { webViewRef = it },
                )
            }
        }
    }
}

/** JS↔原生桥：视频进度实时回传 */
private class BrushBridge(
    private val onProgress: (String, Int, Int) -> Unit,
) {
    @JavascriptInterface
    fun onProgress(title: String, current: Double, duration: Double, switched: Int) {
        val pct = if (duration > 0) ((current / duration) * 100).toInt().coerceIn(0, 100) else 0
        onProgress(title, pct, switched)
    }
}

/** 自动刷注入脚本（精简自 EWT360-Helper：连播/倍速/过检/锁进度/跳题 + 进度回传） */
private val BRUSH_JS = """
(function(){
  if (window.__ewtBrushOn !== undefined) { window.__ewtBrushOn = true; return; }
  window.__ewtBrushOn = true;
  var switched = 0, checked = 0, skipped = 0;

  var st = document.createElement('style');
  st.id = '__ewtLockStyle';
  st.textContent = '[class*="progress"],[class*="prgs"]{pointer-events:none!important;}';
  document.head.appendChild(st);

  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var v = document.querySelector('video');
      if (v && v.playbackRate !== 2) { v.playbackRate = 2; try { v.play(); } catch(e){} }
      var items = document.querySelectorAll('.vjs-menu-content .vjs-menu-item');
      for (var i=0;i<items.length;i++){
        var t = items[i].querySelector('.vjs-menu-item-text');
        if (t && t.textContent.trim() === '2X' && !items[i].classList.contains('vjs-selected')) items[i].click();
      }
    } catch(e){}
  }, 3000);

  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var b = document.querySelector('span.btn-DOCWn');
      if (b && b.textContent.trim() === '点击通过检查' && !b.dataset.ewtClicked) {
        b.dataset.ewtClicked = '1';
        b.click();
        checked++;
        setTimeout(function(){ delete b.dataset.ewtClicked; }, 3000);
      }
    } catch(e){}
  }, 1500);

  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var root = document.querySelector('[class*="dialog"],[class*="modal"],[class*="popup"],[class*="mask"]');
      var scope = root || document;
      var all = scope.querySelectorAll('button');
      for (var i=0;i<all.length;i++){
        var b = all[i];
        if (b.disabled) continue;
        var t = (b.textContent || '').trim();
        if (t === '跳过' || t === '知道了' || t === '继续观看' || t === '确定') {
          if (b.offsetParent !== null) { b.click(); skipped++; }
          break;
        }
      }
    } catch(e){}
  }, 3000);

  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var video = document.querySelector('video');
      if (!video || !video.duration || isNaN(video.duration) || video.duration <= 0) return;
      try { AndroidBridge.onProgress(document.title || '', video.currentTime, video.duration, switched); } catch(e){}
      if (video.currentTime / video.duration < 0.85) return;
      var list = document.querySelector('.listCon-zrsBh') || document.querySelector('[class*="listCon"]');
      if (!list) return;
      var items = list.querySelectorAll('.item-blpma').length
        ? list.querySelectorAll('.item-blpma')
        : list.querySelectorAll('[class*="item"]');
      var active = list.querySelector('.active-EI2Hl') || list.querySelector('[class*="active"]');
      if (!active || items.length === 0) return;
      var idx = Array.prototype.indexOf.call(items, active);
      if (idx < 0 || idx + 1 >= items.length) return;
      items[idx + 1].click();
      switched++;
      setTimeout(function(){
        if (!window.__ewtBrushOn) return;
        var v2 = document.querySelector('video');
        if (v2) { v2.playbackRate = 2; try { v2.play(); } catch(e){} }
      }, 1500);
    } catch(e){}
  }, 2000);
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
private fun createBrushWebView(
    context: Context,
    onProgress: (String, Int, Int) -> Unit,
    onUrl: (String) -> Unit,
): WebView {
    val wv = WebView(context)
    wv.settings.javaScriptEnabled = true
    wv.settings.domStorageEnabled = true
    wv.settings.databaseEnabled = true
    wv.settings.allowFileAccess = false
    wv.settings.mediaPlaybackRequiresUserGesture = false
    wv.addJavascriptInterface(BrushBridge(onProgress), "AndroidBridge")
    // 注入 app token 到 cookie：接续登录态
    val token = runCatching { SecureTokenStore(context).load() }.getOrNull()
    if (!token.isNullOrBlank()) {
        CookieManager.getInstance().setCookie("https://web.ewt360.com/", "token=$token")
        CookieManager.getInstance().setCookie("https://www.ewt360.com/", "token=$token")
        CookieManager.getInstance().setCookie("https://ewt360.com/", "token=$token")
        CookieManager.getInstance().flush()
    }
    wv.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            view?.evaluateJavascript("window.__ewtBrushOn = false;", null)
            if (url != null) onUrl(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            view?.evaluateJavascript(
                "try{var v=document.querySelector('video');if(v){v.muted=true;v.playbackRate=2;}}catch(e){} window.__ewtBrushOn = true;",
                null,
            )
            if (url != null) onUrl(url)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = shouldBlock(url)

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
            shouldBlock(request?.url?.toString())
    }
    wv.setDownloadListener(DownloadListener { url, _, _, _, _ ->
        if (shouldBlock(url)) {
            // 拦截下载（apk 等），不处理
        }
    })
    CookieManager.getInstance().setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
    }
    wv.loadUrl("https://web.ewt360.com/")
    return wv
}

/**
 * 拦截：非 http(s) scheme、应用市场、APK 直链、app 下载引导页（www.ewt360.com 主站）。
 */
private fun shouldBlock(url: String?): Boolean {
    val u = url ?: return true
    if (!(u.startsWith("http://") || u.startsWith("https://"))) return true
    if (u.contains("play.google.com") || u.contains("appgallery") || u.endsWith(".apk")) return true
    if (u.contains("from=appDownloadPage")) return true
    if (u.startsWith("https://www.ewt360.com") || u.startsWith("http://www.ewt360.com")) return true
    return false
}
