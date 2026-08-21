package com.fuck.ewt.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuck.ewt.data.TokenExtractor
import com.fuck.ewt.data.UserInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen(onLoggedIn: (UserInfo) -> Unit) {
    val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
    val status by vm.status.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(false) }

    // 轮询 Cookie：EWT 登录后 token 写入 Cookie，自动检测并校验
    LaunchedEffect(Unit) {
        while (isActive && !vm.isAccepted()) {
            val token = TokenExtractor.readFromCookieManager()
            if (token != null) {
                vm.onTokenDetected(token, onLoggedIn)
            }
            delay(800)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.destroy()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        // 顶部标题区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(
                text = "EWT360 答案查询",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "登录升学 e 网通后自动获取登录态，仅查询与展示答案",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }

        // WebView 登录区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Card(cornerRadius = 20.dp) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx -> createLoginWebView(ctx) { pageLoading = it } },
                        modifier = Modifier.fillMaxSize(),
                        update = { view -> webViewRef = view },
                    )
                    if (pageLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        // 状态提示区
        when (status) {
            LoginViewModel.Status.Validating -> {
                StatusBar(text = "已检测到登录态，正在校验…", onReload = { webViewRef?.reload() })
            }
            LoginViewModel.Status.Invalid -> {
                StatusBar(text = "登录态无效，请在页面中重新登录", onReload = { webViewRef?.reload() })
            }
            else -> {
                StatusBar(text = "请在页面中登录升学 e 网通（登录后自动进入）", onReload = { webViewRef?.reload() })
            }
        }
    }
}

@Composable
private fun StatusBar(text: String, onReload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(text = "刷新页面", onClick = onReload)
    }
}

/**
 * 是否允许该 URL 在 WebView 内继续加载。
 * 仅放行 http/https；EWT360 网页在 Android 上会尝试跳转
 * `intent://` / `mistong://` / `ewt://` 等自定义 scheme 以唤起原生 App，
 * WebView 不支持这些 scheme，直接拦截以免出现 ERR_UNKNOWN_URL_SCHEME。
 */
private fun shouldLoadInWebView(url: String?): Boolean {
    val u = url ?: return false
    if (u.startsWith("http://") || u.startsWith("https://")) {
        return false // 不拦截，WebView 正常加载
    }
    return true // 拦截未知 scheme
}

@SuppressLint("SetJavaScriptEnabled")
private fun createLoginWebView(
    context: Context,
    onPageLoading: (Boolean) -> Unit,
): WebView {
    val wv = WebView(context)
    wv.settings.javaScriptEnabled = true
    wv.settings.domStorageEnabled = true
    wv.settings.databaseEnabled = true
    wv.settings.allowFileAccess = false
    wv.settings.saveFormData = false
    wv.settings.savePassword = false
    wv.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            onPageLoading(true)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onPageLoading(false)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return shouldLoadInWebView(url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return shouldLoadInWebView(request?.url?.toString())
        }
    }
    CookieManager.getInstance().setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
    }
    wv.loadUrl("https://web.ewt360.com/")
    return wv
}
