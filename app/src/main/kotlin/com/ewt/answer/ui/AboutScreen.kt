package com.ewt.answer.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import java.io.File

/**
 * 设置页（底部 Tab）：大标题 miuix 原生排版。
 * 含刷课设置（并行路数 / QPS / 爆发，点击弹锚定气泡）+ 导出详细日志。
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
    var anchor by remember { mutableStateOf<String?>(null) }

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

            // ── 刷课设置 ──
            item(key = "brush_title") {
                SmallTitle("刷课")
            }
            item(key = "brush_concurrency") {
                SettingParamRow(
                    label = "并行路数",
                    desc = "同时刷几个课时。越大越快，但过快易触发风控；默认 6，稳妥可降到 1–2。",
                    value = brushSettings.concurrency,
                    onClick = { anchor = "concurrency" },
                )
            }
            item(key = "brush_qps") {
                SettingParamRow(
                    label = "QPS",
                    desc = "网关全局限速（请求/分钟）。配合并行路数，防 429 风控拦截；默认 150，网络稳可升到 300–400。",
                    value = brushSettings.qps,
                    onClick = { anchor = "qps" },
                )
            }
            item(key = "brush_burst") {
                SettingParamRow(
                    label = "爆发",
                    desc = "单课时竞态爆发并发路数，用并发冗余加速进度累加；默认 8，过高易触发 WAF。",
                    value = brushSettings.burst,
                    onClick = { anchor = "burst" },
                )
            }
            item(key = "export_log") {
                ActionCard(
                    title = "导出详细日志",
                    subtitle = "分享 brush.log 完整刷课日志",
                    onClick = {
                        val logFile = File(context.filesDir, "brush.log")
                        val text = if (logFile.exists()) logFile.readText() else "（暂无刷课日志）"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "EWT 刷课日志")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "导出刷课日志")) }
                    },
                )
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

        // 锚定式气泡弹窗：纵向数值列表 + 参数说明（miuix Card 圆角 + 阴影）
        when (anchor) {
            "concurrency" -> SettingValuePopup(
                title = "并行路数",
                desc = "同时刷几个课时。越大越快，但过快易触发风控；默认 6，稳妥可降到 1–2。",
                options = listOf("1", "2", "4", "6", "8", "12"),
                selected = brushSettings.concurrency,
                onSelect = { onBrushSettingsChange(brushSettings.copy(concurrency = it)); anchor = null },
                onDismiss = { anchor = null },
            )
            "qps" -> SettingValuePopup(
                title = "QPS",
                desc = "网关全局限速（请求/分钟）。配合并行路数，防 429 风控拦截；默认 150，网络稳可升到 300–400。",
                options = listOf("50", "100", "150", "200", "300", "400"),
                selected = brushSettings.qps,
                onSelect = { onBrushSettingsChange(brushSettings.copy(qps = it)); anchor = null },
                onDismiss = { anchor = null },
            )
            "burst" -> SettingValuePopup(
                title = "爆发",
                desc = "单课时竞态爆发并发路数，用并发冗余加速进度累加；默认 8，过高易触发 WAF。",
                options = listOf("4", "6", "8", "12", "16"),
                selected = brushSettings.burst,
                onSelect = { onBrushSettingsChange(brushSettings.copy(burst = it)); anchor = null },
                onDismiss = { anchor = null },
            )
        }
    }
}

/** 设置项行：标签 + 当前值 + 说明，点击弹气泡 */
@Composable
private fun SettingParamRow(
    label: String,
    desc: String,
    value: String,
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
    }
}

/** 锚定式气泡：纵向平铺可选数值（小号、选中蓝色、统一圆角、阴影） */
@Composable
private fun SettingValuePopup(
    title: String,
    desc: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = Modifier
                .width(200.dp)
                .padding(top = 130.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.padding(top = 4.dp))
                options.forEach { opt ->
                    val isSel = opt == selected
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSel) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent,
                            ),
                    ) {
                        TextButton(
                            text = opt,
                            onClick = { onSelect(opt) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
            TopAppBar(
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
