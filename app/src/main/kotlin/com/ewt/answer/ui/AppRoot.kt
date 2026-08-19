package com.ewt.answer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 应用内页面 */
sealed class Screen {
    data object Boot : Screen()
    data object Login : Screen()
    data object Home : Screen()
    data object LinkQuery : Screen()
    data object Debug : Screen()
    data class Questions(val paper: Paper) : Screen()
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme() else lightColorScheme()

    // 动态加载 MiSans 字体（首次下载缓存，失败回退系统字体）
    var miSans by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(Unit) {
        miSans = MiuixFonts.loadMiSans(context)
    }
    val textStyles = remember(miSans) {
        miSans?.let { miSansTextStyles(it) } ?: defaultTextStyles()
    }

    MiuixTheme(colors = colors, textStyles = textStyles) {
        val repo = AppContainer.repository
        var screen by remember { mutableStateOf<Screen>(Screen.Boot) }
        var userInfo by remember { mutableStateOf<UserInfo?>(null) }
        // paperId → 真实题数（打开试卷后回传，主页展示）
        var paperCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

        // 启动：校验已保存的登录态
        LaunchedEffect(Unit) {
            screen = if (repo.hasToken()) {
                try {
                    userInfo = repo.fetchUserInfo()
                    Screen.Home
                } catch (e: Exception) {
                    repo.clearToken()
                    Screen.Login
                }
            } else {
                Screen.Login
            }
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(animationSpec = tween(240)) + slideInHorizontally(animationSpec = tween(300)) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Boot -> BootScreen()
                Screen.Login -> LoginScreen(
                    onLoggedIn = { info ->
                        userInfo = info
                        screen = Screen.Home
                    },
                )
                Screen.Home -> HomeScreen(
                    userInfo = userInfo,
                    paperCounts = paperCounts,
                    onOpenPaper = { paper -> screen = Screen.Questions(paper) },
                    onOpenLinkQuery = { screen = Screen.LinkQuery },
                    onOpenDebug = { screen = Screen.Debug },
                    onLogout = {
                        repo.clearToken()
                        screen = Screen.Login
                    },
                )
                Screen.LinkQuery -> LinkQueryScreen(
                    onBack = { screen = Screen.Home },
                    onOpenPaper = { paper -> screen = Screen.Questions(paper) },
                )
                Screen.Debug -> DebugScreen(
                    onBack = { screen = Screen.Home },
                )
                is Screen.Questions -> QuestionsScreen(
                    paper = s.paper,
                    onBack = { screen = Screen.Home },
                    onPaperOpened = { paperId, count ->
                        if (count > 0) paperCounts = paperCounts + (paperId to count)
                    },
                )
            }
        }
    }
}

/** 将 MiSans 字体应用到 Miuix 全部文本样式 */
private fun miSansTextStyles(fontFamily: FontFamily): TextStyles {
    val base = defaultTextStyles()
    return TextStyles(
        main = base.main.copy(fontFamily = fontFamily),
        paragraph = base.paragraph.copy(fontFamily = fontFamily),
        body1 = base.body1.copy(fontFamily = fontFamily),
        body2 = base.body2.copy(fontFamily = fontFamily),
        button = base.button.copy(fontFamily = fontFamily),
        footnote1 = base.footnote1.copy(fontFamily = fontFamily),
        footnote2 = base.footnote2.copy(fontFamily = fontFamily),
        headline1 = base.headline1.copy(fontFamily = fontFamily),
        headline2 = base.headline2.copy(fontFamily = fontFamily),
        subtitle = base.subtitle.copy(fontFamily = fontFamily),
        title1 = base.title1.copy(fontFamily = fontFamily),
        title2 = base.title2.copy(fontFamily = fontFamily),
        title3 = base.title3.copy(fontFamily = fontFamily),
        title4 = base.title4.copy(fontFamily = fontFamily),
    )
}

@Composable
private fun BootScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
