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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页（底部 Tab）：字体设置 + 关于入口 + 调试模式 + 退出登录 */
@Composable
fun AboutScreen(
    fontEnabled: Boolean,
    fontMb: String,
    onFontEnabledChange: (Boolean) -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    // 顶栏独立 backdrop：只捕获内容区（不含顶栏自身），避免 RenderNode 循环引用崩溃
    val topBarBackdrop = rememberLayerBackdrop()
    val glassSurface = MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                // 与内容 16dp 左对齐
                titlePadding = 16.dp,
                // 液态玻璃顶栏：模糊内容层（Android 12+），低版本降级为半透明底色
                modifier = Modifier.drawBackdrop(
                    backdrop = topBarBackdrop,
                    shape = { RectangleShape },
                    effects = { blur(10f.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(glassSurface.copy(alpha = 0.62f))
                    },
                ),
                color = Color.Transparent,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(topBarBackdrop),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 版本信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column {
                        Text(
                            text = "EWT360 答案查询",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "v1.0.0 · MIUIX 风格 · 答案与解析来自 EWT360 官方接口",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 设置：MiSans 字体
                SmallTitle("设置")
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

                // 关于入口（占位页，后续完善）
                ActionCard(
                    title = "关于",
                    subtitle = "版本信息 / 项目说明",
                    onClick = onOpenAbout,
                )

                // 调试模式入口
                ActionCard(
                    title = "调试模式",
                    subtitle = "查看日志 / 分享日志 / 下载字体",
                    onClick = onOpenDebug,
                )

                // 退出登录
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

/** 关于页（占位，后续完善） */
@Composable
fun AboutPlaceholderScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
                titlePadding = 16.dp,
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
                    text = "关于页面建设中",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "敬请期待",
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
