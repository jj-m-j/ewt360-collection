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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewt.answer.data.CourseRepository
import com.ewt.answer.data.SecureTokenStore
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课程页（WebView 刷课版）：
 * 大标题（miuix title1 排版）→ 概览卡（课时统计 + 开始/停止 + 实时进度 + URL 复制）→ WebView（真实浏览器环境）。
 * WebView 加载前注入 app token 到 cookie（接续登录态，不依赖 web 会话存活），默认 UA（与登录页一致，渲染正常）；
 * 注入 JS 自动连播（85% 切换 + 2 倍速 + 自动过检 + 锁进度条 + 跳题），JS↔原生桥实时回传进度；
 * 拦截 ewt app 跳转/下载/下载引导页。
 */
@Composable
fun CourseScreen(
    onOpenSettings: () -> Unit = {},
) {
    val vm: com.ewt.answer.ui.CourseViewModel = viewModel(
        factory = com.ewt.answer.ui.CourseViewModel.Factory,
    )
    val uiState by vm.uiState.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val brushingAll by vm.brushingAll.collectAsState()
    val summary by vm.summary.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val context = LocalContext.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var brushing by remember { mutableStateOf(false) }
    // 可视化状态（JS 桥回传）
    var lessonTitle by remember { mutableStateOf("未开始") }
    var progress by remember { mutableIntStateOf(0) }
    var switchedCount by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ewt_prefs", android.content.Context.MODE_PRIVATE)
        CourseRepository.burstSize = prefs.getInt("course_burst", 1)
        vm.load()
    }

    // 课时统计（概览卡）
    val total = lessons.size
    val doneCount = lessons.values.count { it.done || it.lesson.finished }
    val pending = (total - doneCount).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── 大标题行（miuix 原生排版：左右 26dp 边距、下方留白） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "课程",
                    fontSize = MiuixTheme.textStyles.title1.fontSize,
                    fontWeight = FontWeight.Normal,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "网页刷课模式 · 2 倍速",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(
                onClick = { vm.load(force = true) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = RefreshIcon,
                    contentDescription = "刷新",
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = SettingsTabIcon,
                    contentDescription = "课程设置",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }

        // ── 概览卡（固定高度，避免进度更新导致 WebView 重排闪烁） ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                // 课时统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatItem("共 $total", "课时")
                    StatItem("$pending", "未完成")
                    StatItem("$doneCount", "已完成")
                }
                Spacer(Modifier.height(10.dp))
                // 当前课时 + 进度（固定行高）
                Text(
                    text = if (brushing) "正在刷：$lessonTitle" else "当前：$lessonTitle",
                    fontSize = 14.sp,
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
                    text = "$progress% · 已刷 $switchedCount 课",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                // 控制 + 状态（固定两行高）
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
                            } else {
                                wv.evaluateJavascript("window.__ewtBrushOn = false;", null)
                            }
                        },
                        modifier = Modifier.width(130.dp),
                    ) {
                        Text(if (brushing) "停止" else "开始自动刷", fontSize = 14.sp)
                    }
                    Text(
                        text = when {
                            uiState is com.ewt.answer.ui.CourseViewModel.UiState.Loading -> statusText.ifBlank { "扫描中…" }
                            brushing -> "自动刷运行中"
                            else -> "在下方网页中点开视频课，再点开始"
                        },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                }
                // URL + 复制（固定显示，空时占位，避免高度跳动）
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

        // ── WebView（真实浏览器环境刷课主体） ──
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
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

/** 概览统计小项 */
@Composable
private fun StatItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
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

  // 跳题/跳过弹窗（低频 + 只查可见按钮，避免全页扫描卡顿闪烁）
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

  // 自动连播：85% 进度 → 点列表下一个 + 回传进度
  setInterval(function(){
    if (!window.__ewtBrushOn) return;
    try {
      var video = document.querySelector('video');
      if (!video || !video.duration || isNaN(video.duration) || video.duration <= 0) return;
      // 回传进度给原生
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
    // 允许自动播放（不要求用户手势）
    wv.settings.mediaPlaybackRequiresUserGesture = false
    // JS↔原生桥
    wv.addJavascriptInterface(BrushBridge(onProgress), "AndroidBridge")
    // 注入 app token 到 cookie：接续登录态（不依赖 web 会话存活），避免跳到下载页
    val token = SecureTokenStore(context).load()
    if (!token.isNullOrBlank()) {
        CookieManager.getInstance().setCookie("https://web.ewt360.com/", "token=$token")
        CookieManager.getInstance().setCookie("https://www.ewt360.com/", "token=$token")
        CookieManager.getInstance().setCookie("https://ewt360.com/", "token=$token")
        CookieManager.getInstance().flush()
    }
    wv.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            // 页面跳转后重置刷课状态，避免旧页面定时器失效
            view?.evaluateJavascript("window.__ewtBrushOn = false;", null)
            if (url != null) onUrl(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            // 静音当前视频 + 恢复自动刷标记
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
    // 拦截 APK 下载 / 应用市场跳转
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
 * 是否拦截该 URL：仅放行 http/https 且非下载引导；
 * 拦截 intent:// / ewt:// / mistong:// 等自定义 scheme、应用市场、APK 直链、app 下载引导页。
 */
private fun shouldBlock(url: String?): Boolean {
    val u = url ?: return true
    if (!(u.startsWith("http://") || u.startsWith("https://"))) return true
    // 应用市场 / APK 直链
    if (u.contains("play.google.com") || u.contains("appgallery") || u.endsWith(".apk")) return true
    // app 下载引导页（ewt360.com 主站，非 web. 子域）
    if (u.contains("from=appDownloadPage")) return true
    if (u.startsWith("https://www.ewt360.com") || u.startsWith("http://www.ewt360.com")) return true
    return false
}
