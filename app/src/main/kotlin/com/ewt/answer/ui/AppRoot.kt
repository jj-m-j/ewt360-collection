package com.ewt.answer.ui

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.EwtRepository
import com.ewt.answer.data.Paper
import com.ewt.answer.data.UserInfo
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 应用内页面 */
sealed class Screen {
    data object Boot : Screen()
    data object Login : Screen()
    /** 主层（底部 Tab：0=课程 1=试卷 2=设置） */
    data object Main : Screen()
    data object LinkQuery : Screen()
    data object Debug : Screen()
    /** 关于（占位页，从设置页进入） */
    data object About : Screen()
    /** 刷指定课程：选择未刷课程加入队列（从课程页进入） */
    data object CoursePick : Screen()
    data class Questions(val paper: Paper) : Screen()
}

/** 底部 Tab 索引：课程 / 试卷 / 设置 */
private const val TAB_COURSE = 0
private const val TAB_PAPERS = 1
private const val TAB_SETTINGS = 2

/** 弹窗底部操作栏：取消在上、确认在下（纵向布局，全应用统一） */
@Composable
internal fun DialogActions(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确认",
    dismissText: String = "取消",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        TextButton(
            text = dismissText,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(confirmText, fontSize = 14.sp)
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme() else lightColorScheme()

    // 字体偏好（首次询问后决定）
    val prefs = remember { context.getSharedPreferences("ewt_prefs", Context.MODE_PRIVATE) }
    var fontEnabled by remember { mutableStateOf(prefs.getBoolean("font_enabled", false)) }
    var fontPrompted by remember { mutableStateOf(prefs.getBoolean("font_prompted", false)) }
    var showFontPrompt by remember { mutableStateOf(false) }

    // 动态加载 MiSans 字体（已下载则直接使用本地缓存，不重复下载）
    var miSans by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(fontEnabled) {
        if (fontEnabled) {
            miSans = MiuixFonts.loadMiSans(context)
        } else {
            miSans = null
        }
    }
    // 首次使用：询问是否下载字体
    LaunchedEffect(Unit) {
        if (!fontPrompted && !MiuixFonts.isDownloaded(context) && !fontEnabled) {
            showFontPrompt = true
        } else if (fontEnabled && miSans == null) {
            miSans = MiuixFonts.loadMiSans(context)
        }
    }
    val textStyles = remember(miSans) {
        miSans?.let { miSansTextStyles(it) } ?: defaultTextStyles()
    }

    MiuixTheme(colors = colors, textStyles = textStyles) {
        val repo = AppContainer.repository
        var screen by remember { mutableStateOf<Screen>(Screen.Boot) }
        var previous by remember { mutableStateOf<Screen?>(null) }
        var userInfo by remember { mutableStateOf<UserInfo?>(null) }
        // 主层底部 Tab（课程 / 试卷 / 设置）
        var tab by remember { mutableIntStateOf(TAB_COURSE) }
        // paperId → 真实题数（打开试卷后回传，主页展示）
        var paperCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        // 课程刷课参数（跨课程页/设置页共享）
        var brushSettings by remember { mutableStateOf(BrushSettings()) }
        // 主页列表滚动位置（跨页面保留，返回不跳顶）
        val homeListState: LazyListState = rememberLazyListState()

        // ── 预测式返回：手势进度驱动（下层 25% 视差 + 0.9→1.0 渐入；Scale/Translation/Alpha/圆角统一由 backProgress 驱动） ──
        val backProgress = remember { Animatable(0f) }
        var showPrevLayer by remember { mutableStateOf(false) }
        var gestureCommitted by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun navigateTo(target: Screen) {
            if (target != screen) {
                previous = screen
                screen = target
                showPrevLayer = false
                scope.launch { backProgress.snapTo(0f) }
            }
        }

        /**
         * 返回（预测式/系统键）：
         * 松手后从当前进度以 FastOutSlowIn 曲线补到完全划出（自然增强退出感，不瞬移），
         * 再切换到上一页并继续滑出淡出。
         */
        fun goBack() {
            val prev = previous
            if (prev == null) {
                scope.launch { backProgress.snapTo(0f) }
                return
            }
            if (gestureCommitted) return // 动画进行中，防重入
            gestureCommitted = true
            scope.launch {
                // 接近阈值自然增强退出感（快速起步 → 柔和到位）
                backProgress.animateTo(
                    1f,
                    tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
                screen = prev
                showPrevLayer = true
                backProgress.animateTo(
                    1.2f,
                    tween(durationMillis = 240, easing = LinearEasing),
                )
                previous = null
                showPrevLayer = false
                gestureCommitted = false
                backProgress.snapTo(0f)
            }
        }

        // 系统预测式返回回调
        val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val backCallback = remember {
            object : OnBackPressedCallback(true) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (previous != null && !gestureCommitted) {
                        showPrevLayer = true
                        gestureCommitted = false
                        scope.launch { backProgress.snapTo(backEvent.progress.coerceIn(0f, 1f)) }
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (!gestureCommitted) {
                        // 跟手：连续稳定
                        scope.launch { backProgress.snapTo(backEvent.progress.coerceIn(0f, 1f)) }
                    }
                }

                override fun handleOnBackCancelled() {
                    if (!gestureCommitted) {
                        showPrevLayer = false
                        scope.launch {
                            // 取消返回：克制的 Spring 恢复（阻尼 0.95，几乎无可见回弹）
                            backProgress.animateTo(
                                0f,
                                spring(dampingRatio = 0.95f, stiffness = 350f),
                            )
                        }
                    }
                }

                override fun handleOnBackPressed() {
                    goBack()
                }
            }
        }
        DisposableEffect(dispatcher, backCallback) {
            dispatcher?.addCallback(backCallback)
            onDispose { backCallback.remove() }
        }
        backCallback.isEnabled =
            screen !is Screen.Main && screen !is Screen.Login && screen !is Screen.Boot

        // 启动：校验登录态
        LaunchedEffect(Unit) {
            screen = if (repo.hasToken()) {
                try {
                    userInfo = repo.fetchUserInfo()
                    Screen.Main
                } catch (e: Exception) {
                    repo.clearToken()
                    Screen.Login
                }
            } else {
                Screen.Login
            }
        }

        // ── 双层渲染：背景=上一页（手势/动画渐入），顶层=当前页（手势跟随） ──
        Box(Modifier.fillMaxSize()) {
            // 背景层（25% 视差 + alpha 0.9→1.0 渐入；退出阶段保持原位）
            if (previous != null && (previous == Screen.Main || showPrevLayer)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = backProgress.value
                            alpha = (0.9f + 0.1f * p).coerceAtMost(1f)
                            translationX = if (p <= 1f) -0.25f * size.width * (1f - p) else 0f
                        },
                ) {
                    RenderScreen(
                        screen = previous!!,
                        userInfo = userInfo,
                        paperCounts = paperCounts,
                        listState = homeListState,
                        tab = tab,
                        onTabSelect = { tab = it },
                        repo = repo,
                        brushSettings = brushSettings,
                        onBrushSettingsChange = { brushSettings = it },
                        fontEnabled = fontEnabled,
                        onFontEnabledChange = { en ->
                            // 仅切换启用状态：不删除已下载字体，重新开启免下载
                            fontEnabled = en
                            prefs.edit().putBoolean("font_enabled", en).apply()
                        },
                        navigateTo = { navigateTo(it) },
                        onBack = { goBack() },
                        onPaperOpened = { id, c -> if (c > 0) paperCounts = paperCounts + (id to c) },
                    )
                }
            }
            // 顶层：当前页（手势跟随；退出阶段继续右滑并淡出，不盖回）
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = backProgress.value
                        // Translation / Alpha / Corner 统一由同一进度驱动，节奏一致
                        translationX = size.width * p
                        alpha = if (p >= 1f) (2f - p).coerceIn(0f, 1f) else 1f
                        if (p > 0f) {
                            shape = RoundedCornerShape(28.dp.toPx() * p)
                            clip = true
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        when {
                            gestureCommitted -> fadeIn(tween(1)).togetherWith(fadeOut(tween(1)))
                            targetState is Screen.Questions ||
                                targetState is Screen.LinkQuery ||
                                targetState is Screen.Debug ||
                                targetState is Screen.About ||
                                targetState is Screen.CoursePick -> {
                                (fadeIn(tween(240)) + slideInHorizontally(tween(300)) { it / 3 })
                                    .togetherWith(fadeOut(tween(200)))
                            }
                            else -> {
                                (fadeIn(tween(240)) + slideInHorizontally(tween(300)) { -it / 3 })
                                    .togetherWith(fadeOut(tween(200)))
                            }
                        }
                    },
                    label = "screen",
                ) { s ->
                    RenderScreen(
                        screen = s,
                        userInfo = userInfo,
                        paperCounts = paperCounts,
                        listState = homeListState,
                        tab = tab,
                        onTabSelect = { tab = it },
                        repo = repo,
                        brushSettings = brushSettings,
                        onBrushSettingsChange = { brushSettings = it },
                        fontEnabled = fontEnabled,
                        onFontEnabledChange = { en ->
                            // 仅切换启用状态：不删除已下载字体，重新开启免下载
                            fontEnabled = en
                            prefs.edit().putBoolean("font_enabled", en).apply()
                        },
                        navigateTo = { navigateTo(it) },
                        onBack = { goBack() },
                        onPaperOpened = { id, c -> if (c > 0) paperCounts = paperCounts + (id to c) },
                    )
                }
            }
        }

        // 首次使用：字体下载询问
        if (showFontPrompt) {
            WindowDialog(
                show = true,
                title = "下载 MiSans 字体？",
                summary = "提升显示效果",
                onDismissRequest = {
                    prefs.edit().putBoolean("font_prompted", true).apply()
                    showFontPrompt = false
                },
            ) {
                Column {
                    Text(
                        text = "MiSans 为小米系统级字体，下载后界面显示效果更佳。占用约 20MB 空间；字体文件下载后长期保留，可在「设置」中随时切换启用状态，关闭不会删除、再次开启无需重新下载。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    DialogActions(
                        onDismiss = {
                            prefs.edit().putBoolean("font_prompted", true).apply()
                            showFontPrompt = false
                        },
                        onConfirm = {
                            prefs.edit()
                                .putBoolean("font_prompted", true)
                                .putBoolean("font_enabled", true)
                                .apply()
                            fontEnabled = true
                            showFontPrompt = false
                        },
                        confirmText = "下载",
                    )
                }
            }
        }
    }
}

/** 渲染单个页面（供顶层与背景层复用） */
@Composable
private fun RenderScreen(
    screen: Screen,
    userInfo: UserInfo?,
    paperCounts: Map<String, Int>,
    listState: LazyListState,
    tab: Int,
    onTabSelect: (Int) -> Unit,
    repo: EwtRepository,
    brushSettings: BrushSettings,
    onBrushSettingsChange: (BrushSettings) -> Unit,
    fontEnabled: Boolean,
    onFontEnabledChange: (Boolean) -> Unit,
    navigateTo: (Screen) -> Unit,
    onBack: () -> Unit,
    onPaperOpened: (String, Int) -> Unit,
) {
    when (screen) {
        Screen.Boot -> BootScreen()
        Screen.Login -> LoginScreen(
            onLoggedIn = { navigateTo(Screen.Main) },
        )
        Screen.Main -> MainLayer(
            userInfo = userInfo,
            paperCounts = paperCounts,
            listState = listState,
            tab = tab,
            onTabSelect = onTabSelect,
            brushSettings = brushSettings,
            onBrushSettingsChange = onBrushSettingsChange,
            fontEnabled = fontEnabled,
            onFontEnabledChange = onFontEnabledChange,
            onOpenPaper = { paper -> navigateTo(Screen.Questions(paper)) },
            onOpenLinkQuery = { navigateTo(Screen.LinkQuery) },
            onOpenDebug = { navigateTo(Screen.Debug) },
            onOpenAbout = { navigateTo(Screen.About) },
            onOpenCoursePick = { navigateTo(Screen.CoursePick) },
            onLogout = {
                repo.clearToken()
                navigateTo(Screen.Login)
            },
        )
        Screen.LinkQuery -> LinkQueryScreen(
            onBack = onBack,
            onOpenPaper = { paper -> navigateTo(Screen.Questions(paper)) },
        )
        Screen.Debug -> DebugScreen(
            onBack = onBack,
        )
        Screen.About -> AboutPlaceholderScreen(
            onBack = onBack,
        )
        Screen.CoursePick -> CoursePickScreen(
            onBack = onBack,
        )
        is Screen.Questions -> QuestionsScreen(
            paper = screen.paper,
            onBack = onBack,
            onPaperOpened = onPaperOpened,
        )
    }
}

/** 主层：页面内容（作为液态玻璃底栏 backdrop 源）+ 液态玻璃悬浮底栏（窄胶囊，居中悬浮） */
@Composable
private fun MainLayer(
    userInfo: UserInfo?,
    paperCounts: Map<String, Int>,
    listState: LazyListState,
    tab: Int,
    onTabSelect: (Int) -> Unit,
    brushSettings: BrushSettings,
    onBrushSettingsChange: (BrushSettings) -> Unit,
    fontEnabled: Boolean,
    onFontEnabledChange: (Boolean) -> Unit,
    onOpenPaper: (Paper) -> Unit,
    onOpenLinkQuery: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCoursePick: () -> Unit,
    onLogout: () -> Unit,
) {
    // 玻璃底栏的反射源：捕获整个 Tab 内容（列表滚动画面；顶栏 blur 在页面内部用独立 backdrop，避免成环）
    val backdrop = rememberLayerBackdrop()
    // 稳定 lambda + 动态读取当前 tab（避免捕获旧值导致底栏不同步）
    val currentTab = rememberUpdatedState(tab)
    val selectedTabIndex = remember { { currentTab.value } }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            transitionSpec = {
                // Tab 切换：按方向滑动 + 淡入淡出（FastOutSlowIn 曲线，MIUIX 动效）
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { direction * it / 4 })
                    .togetherWith(
                        fadeOut(tween(150)) + slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -direction * it / 4 },
                    )
            },
            label = "tab",
        ) { t ->
            when (t) {
                TAB_COURSE -> CourseBrushScreen(
                    settings = brushSettings,
                    onOpenCoursePick = onOpenCoursePick,
                )
                TAB_PAPERS -> HomeScreen(
                    userInfo = userInfo,
                    paperCounts = paperCounts,
                    listState = listState,
                    onOpenPaper = onOpenPaper,
                    onOpenLinkQuery = onOpenLinkQuery,
                )
                else -> AboutScreen(
                    fontEnabled = fontEnabled,
                    fontMb = MiuixFonts.downloadedMb(LocalContext.current),
                    onFontEnabledChange = onFontEnabledChange,
                    onOpenDebug = onOpenDebug,
                    onOpenAbout = onOpenAbout,
                    onLogout = onLogout,
                    brushSettings = brushSettings,
                    onBrushSettingsChange = onBrushSettingsChange,
                )
            }
        }
        // 液态玻璃悬浮底栏：内容包裹窄胶囊，居中悬浮（在内容层之外采样，无递归）
        LiquidGlassBottomBar(
            tabs = listOf(
                LiquidGlassTab(icon = CourseTabIcon, label = "课程"),
                LiquidGlassTab(icon = PaperTabIcon, label = "试卷"),
                LiquidGlassTab(icon = SettingsTabIcon, label = "设置"),
            ),
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelect,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        )
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
