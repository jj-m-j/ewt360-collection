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
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 应用内页面 */
sealed class Screen {
    data object Boot : Screen()
    data object Login : Screen()
    data object Home : Screen()
    data class Questions(val paper: Paper) : Screen()
}

@Composable
fun AppRoot() {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme() else lightColorScheme()

    MiuixTheme(colors = colors) {
        val repo = AppContainer.repository
        var screen by remember { mutableStateOf<Screen>(Screen.Boot) }
        var userInfo by remember { mutableStateOf<UserInfo?>(null) }

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
                    onOpenPaper = { paper -> screen = Screen.Questions(paper) },
                    onLogout = {
                        repo.clearToken()
                        screen = Screen.Login
                    },
                )
                is Screen.Questions -> QuestionsScreen(
                    paper = s.paper,
                    onBack = { screen = Screen.Home },
                )
            }
        }
    }
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
