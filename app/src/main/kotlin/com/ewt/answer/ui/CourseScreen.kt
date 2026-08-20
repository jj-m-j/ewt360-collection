package com.ewt.answer.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课程页：WebView 打开 EWT360 学习页（site-study），
 * 页面加载完成后注入刷课助手脚本（源自 EWT360-Helper，assets/ewt_helper.js）：
 * 自动跳题 / 自动连播 / 自动过检 / 2倍速 / 刷课模式（右下角 📚 悬浮面板控制）。
 */
@Composable
fun CourseScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var injected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("加载中…") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "课程",
                titlePadding = 16.dp,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 刷课助手状态提示条
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "刷课助手：$status\n打开课程视频后，点击页面右下角 📚 图标，可开启 自动跳题 / 自动连播 / 自动过检 / 2倍速 / 刷课模式。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        @SuppressLint("SetJavaScriptEnabled")
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!injected) {
                                        injected = true
                                        val js = runCatching {
                                            ctx.assets.open("ewt_helper.js").bufferedReader().readText()
                                        }.getOrNull()
                                        if (!js.isNullOrBlank()) {
                                            view?.evaluateJavascript(js, null)
                                            status = "已注入（打开视频后点右下角 📚）"
                                        } else {
                                            status = "脚本加载失败"
                                        }
                                    }
                                }
                            }
                            loadUrl("https://web.ewt360.com/site-study/")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
