package com.ewt.answer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网页刷课页（WebView 方案）：
 * 加载 web.ewt360.com 真实浏览器环境（复用登录 Cookie），用户进入课程视频页后点「开始自动刷」，
 * 注入 JS 自动连播（85% 切换下一课 + 2 倍速 + 自动过检 + 锁进度条 + 跳题）。
 * 真实 Chromium 环境上报 → 无 699101/699102 风控（官方播放器自己播自己报）。
 */
@Composable
fun WebViewBrushScreen(
    onBack: () -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var brushing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("进入课程视频页后点「开始自动刷」") }

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
            Box(Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx -> createBrushWebView(ctx) },
                    modifier = Modifier.fillMaxSize(),
                    update = { webViewRef = it },
                )
            }
            // 底部控制条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        val wv = webViewRef ?: return@Button
                        brushing = !brushing
                        if (brushing) {
                            wv.evaluateJavascript(BRUSH_JS, null)
                            status = "自动刷运行中：85% 自动切换 · 2 倍速 · 自动过检"
                        } else {
                            wv.evaluateJavascript("window.__ewtBrushOn = false;", null)
                            status = "已停止"
                        }
                    },
                    modifier = Modifier.width(120.dp),
                ) {
                    Text(if (brushing) "停止" else "开始自动刷", fontSize = 14.sp)
                }
                Text(
                    text = status,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 自动刷注入脚本（精简自 EWT360-Helper：连播/倍速/过检/锁进度/跳题） */
private const val BRUSH_JS = """
(function(){
  if (window.__ewtBrushOn !== undefined) { window.__ewtBrushOn = true; return; }
  window.__ewtBrushOn = true;
  var switched = 0, checked = 0, skipped = 0;

  // 锁进度条（防止误拖进度）
  var st = document.createElement('style');
  st.id = '__ewtLockStyle';
  st.textContent = '[class*="progress"],[class*="prgs"]{pointer-events:none!important;}';
  document.head.appendChild(st);

  // 倍速：直接设 video.playbackRate + 尝试点 video.js 的 2X 菜单
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

  // 自动过检（看课检测按钮）
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

  // 跳题/跳过弹窗
  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var all = document.querySelectorAll('button,span,div');
      for (var i=0;i<all.length;i++){
        var t = all[i].textContent;
        if (t && t.trim() === '跳过' && all[i].children.length === 0) { all[i].click(); skipped++; break; }
      }
    } catch(e){}
  }, 1000);

  // 自动连播：85% 进度 → 点列表下一个
  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var video = document.querySelector('video');
      if (!video || !video.duration || isNaN(video.duration) || video.duration <= 0) return;
      var list = document.querySelector('.listCon-zrsBh') || document.querySelector('[class*="listCon"]');
      if (!list) return;
      var items = list.querySelectorAll('.item-blpma') && list.querySelectorAll('.item-blpma').length
        ? list.querySelectorAll('.item-blpma')
        : list.querySelectorAll('[class*="item"]');
      var active = list.querySelector('.active-EI2Hl') || list.querySelector('[class*="active"]');
      if (!active || items.length === 0) return;
      if (video.currentTime / video.duration < 0.85) return;
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
private fun createBrushWebView(context: Context): WebView {
    val wv = WebView(context)
    wv.settings.javaScriptEnabled = true
    wv.settings.domStorageEnabled = true
    wv.settings.databaseEnabled = true
    wv.settings.allowFileAccess = false
    // 允许自动播放（不要求用户手势）
    wv.settings.mediaPlaybackRequiresUserGesture = false
    wv.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            // 页面跳转后重置刷课状态，避免旧页面定时器失效
            view?.evaluateJavascript("window.__ewtBrushOn = false;", null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            // 静音当前视频（自动刷不需要声音）
            view?.evaluateJavascript(
                "try{var v=document.querySelector('video');if(v){v.muted=true;v.playbackRate=2;}}catch(e){}",
                null,
            )
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = shouldBlock(url)

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
            shouldBlock(request?.url?.toString())
    }
    CookieManager.getInstance().setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
    }
    wv.loadUrl("https://web.ewt360.com/")
    return wv
}

/** 只放行 http/https，拦截 intent:// 等自定义 scheme */
private fun shouldBlock(url: String?): Boolean {
    val u = url ?: return true
    return !(u.startsWith("http://") || u.startsWith("https://"))
}
