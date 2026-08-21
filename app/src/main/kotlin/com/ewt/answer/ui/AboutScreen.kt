package com.ewt.answer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** GitHub Octocat 图标（官方 github.svg path 数据） */
private val GithubLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "Github",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes("M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * 设置页（底部 Tab）：大标题 miuix 原生排版。
 * 含刷课设置（并行路数 / QPS / 爆发，点击行下方横向滑出数值选择）+ 调试模式（含导出刷课日志）。
 */
@Composable
fun AboutScreen(
    fontEnabled: Boolean,
    fontMb: String,
    onFontEnabledChange: (Boolean) -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
    brushSettings: BrushSettings,
    onBrushSettingsChange: (BrushSettings) -> Unit,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    // 展开的参数行（null=不展开）
    var expandedParam by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 大标题行（miuix 原生排版）
            item(key = "large_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 0.dp, top = 4.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "设置",
                        fontSize = MiuixTheme.textStyles.title1.fontSize,
                        fontWeight = FontWeight.Normal,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }

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
                            text = "Fuck Ewt · ${versionName(context)}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
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

            // ── 刷课设置 ──
            item(key = "brush_title") {
                SmallTitle("刷课")
            }
            item(key = "brush_concurrency") {
                SettingParamRow(
                    label = "并行路数",
                    desc = "同时刷几个课时。越大越快，但过快易触发风控；默认 6，稳妥可降到 1–2。",
                    value = brushSettings.concurrency,
                    expanded = expandedParam == "concurrency",
                    options = listOf("1", "2", "4", "6", "8", "12"),
                    onSelect = { onBrushSettingsChange(brushSettings.copy(concurrency = it)) },
                    onClick = { expandedParam = if (expandedParam == "concurrency") null else "concurrency" },
                )
            }
            item(key = "brush_qps") {
                SettingParamRow(
                    label = "QPS",
                    desc = "网关全局限速（请求/分钟）。配合并行路数，防 429 风控拦截；默认 150，网络稳可升到 300–400。",
                    value = brushSettings.qps,
                    expanded = expandedParam == "qps",
                    options = listOf("50", "100", "150", "200", "300", "400"),
                    onSelect = { onBrushSettingsChange(brushSettings.copy(qps = it)) },
                    onClick = { expandedParam = if (expandedParam == "qps") null else "qps" },
                )
            }
            item(key = "brush_burst") {
                SettingParamRow(
                    label = "爆发",
                    desc = "单课时竞态爆发并发路数，用并发冗余加速进度累加；默认 8，过高易触发 WAF。",
                    value = brushSettings.burst,
                    expanded = expandedParam == "burst",
                    options = listOf("4", "6", "8", "12", "16"),
                    onSelect = { onBrushSettingsChange(brushSettings.copy(burst = it)) },
                    onClick = { expandedParam = if (expandedParam == "burst") null else "burst" },
                )
            }

            item(key = "about") {
                ActionCard(
                    title = "致谢",
                    subtitle = "本项目所参考的开源项目",
                    onClick = onOpenAbout,
                )
            }

            item(key = "debug") {
                ActionCard(
                    title = "调试模式",
                    subtitle = "查看日志 / 导出刷课日志",
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

/** 设置项行：标签 + 当前值 + 说明，点击在行下方滑出横向数值选择 */
@Composable
private fun SettingParamRow(
    label: String,
    desc: String,
    value: String,
    expanded: Boolean,
    options: List<String>,
    onSelect: (String) -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = value,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.padding(top = 3.dp))
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "修改",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        // 横向数值选择：滑出动画
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { opt ->
                    val isSel = opt == value
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSel) MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.08f),
                            )
                            .clickable(onClick = { onSelect(opt) })
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = opt,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 读取当前构建号（versionName） */
private fun versionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    }.getOrDefault("1.0.0")

/** 致谢页：本项目参考的开源项目 */
@Composable
fun AboutPlaceholderScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "致谢",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "感谢以下开源项目，让本项目成为可能：",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            item {
                CreditCard(name = "EWT-TOOL", url = "https://github.com/ZZ0YY/EWT-TOOL")
            }
            item {
                CreditCard(name = "GetEWTAnswers", url = "https://github.com/zhicheng233/GetEWTAnswers/")
            }
            item {
                CreditCard(name = "ewt360-brush", url = "https://github.com/Zxxaq1478359473/ewt360-brush")
            }
            item {
                CreditCard(name = "miuix", url = "https://github.com/compose-miuix-ui/miuix")
            }
            // GitHub 图标（官方 svg path，居中显示）
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = GithubLogo,
                        contentDescription = "GitHub",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCard(name: String, url: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = url,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
