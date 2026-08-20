package com.ewt.answer.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关于页（底部 Tab）：版本 + 设置（字体/准确率）+ 调试模式 + 退出登录 */
@Composable
fun AboutScreen(
    accuracy: Int,
    onAccuracyChange: (Int) -> Unit,
    fontEnabled: Boolean,
    fontMb: String,
    onFontEnabledChange: (Boolean) -> Unit,
    onOpenDebug: () -> Unit,
    onLogout: () -> Unit,
    backdrop: LayerBackdrop,
) {
    // 顶栏玻璃底色：组合上下文提前捕获（onDrawSurface 为绘制 lambda，非组合上下文）
    val glassSurface = MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
                // 液态玻璃顶栏：真实 backdrop 模糊（Android 12+），低版本自动降级为半透明底色
                modifier = Modifier.drawBackdrop(
                    backdrop = backdrop,
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
                            text = if (fontEnabled && fontMb.isNotBlank()) "已启用（占用 $fontMb）" else "关闭后恢复系统字体并删除已下载",
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
            // 设置：主观题准确率
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "主观题准确率",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "客观题由系统批改；主观题按比例分配 满分 / 半对 / 错",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = "$accuracy%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = accuracy.toFloat() / 100f,
                        onValueChange = { onAccuracyChange((it * 100).toInt().coerceIn(0, 100)) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

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
