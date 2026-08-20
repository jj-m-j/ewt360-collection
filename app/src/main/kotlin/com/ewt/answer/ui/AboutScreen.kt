package com.ewt.answer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页（底部 Tab）：与试卷/课程页统一（miuix 原生 TopAppBar + 上滑收缩 + LiquidGlass 模糊）。
 */
@Composable
fun AboutScreen(
    fontEnabled: Boolean,
    fontMb: String,
    onFontEnabledChange: (Boolean) -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    // 顶栏独立 backdrop：只捕获内容区，避免 RenderNode 循环引用崩溃
    val topBarBackdrop = rememberLayerBackdrop()
    val glassSurface = MiuixTheme.colorScheme.surface
    val listState = rememberLazyListState()

    // miuix 原生：TopAppBar + 上滑收缩
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                modifier = Modifier.drawBackdrop(
                    backdrop = topBarBackdrop,
                    shape = { RectangleShape },
                    effects = { blur(10f.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(glassSurface.copy(alpha = 0.62f))
                    },
                ),
                color = Color.Transparent,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(topBarBackdrop),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "version") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Column {
                            Text(
                                text = "去你妈的e网通",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Fuck Ewt · v1.0.0 · MIUIX 风格 · 答案与解析来自 EWT360 官方接口",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                item(key = "font_title") {
                    SmallTitle("设置")
                }
                item(key = "font") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "MiSans 字体",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = if (fontEnabled && fontMb.isNotBlank()) {
                                        "已启用（占用 $fontMb）"
                                    } else {
                                        "未启用（已下载的字体文件会保留，重新开启免下载）"
                                    },
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Switch(
                                checked = fontEnabled,
                                onCheckedChange = onFontEnabledChange,
                            )
                        }
                    }
                }

                item(key = "about") {
                    ActionCard(
                        title = "关于",
                        subtitle = "版本信息 / 项目说明",
                        onClick = onOpenAbout,
                    )
                }

                item(key = "debug") {
                    ActionCard(
                        title = "调试模式",
                        subtitle = "查看日志 / 分享日志 / 下载字体",
                        onClick = onOpenDebug,
                    )
                }

                item(key = "logout") {
                    ActionCard(
                        title = "退出登录",
                        subtitle = "清除登录状态并返回登录页",
                        titleColor = MiuixTheme.colorScheme.error,
                        onClick = onLogout,
                    )
                }
            }
        }
    }
}

/** 关于页（占位，后续完善） */
@Composable
fun AboutPlaceholderScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = "关于",
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "去你妈的e网通",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "关于页面建设中，敬请期待",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = title,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}
