package com.fuck.ewt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 刷课参数（AppRoot 持有，跨页共享） */
data class BrushSettings(
    val concurrency: String = "6",
    val qps: String = "150",
    val burst: String = "8",
)

/**
 * 课程刷课设置页（二级页）：并行路数 / QPS / 爆发。
 * 点击参数行弹出锚定式纵向气泡，可选数值 + 参数说明。
 */
@Composable
fun CourseBrushSettingsScreen(
    settings: BrushSettings,
    onChange: (BrushSettings) -> Unit,
    onBack: () -> Unit,
) {
    var anchor by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "刷课设置",
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "hint") {
                    Text(
                        text = "点击参数可修改数值；刷课速度与稳定性取决于这三个参数。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                item(key = "concurrency") {
                    SettingParamRow(
                        label = "并行路数",
                        desc = "同时刷几个课时。越大越快，但过快易触发风控；默认 6，稳妥可降到 1–2。",
                        value = settings.concurrency,
                        onClick = { anchor = "concurrency" },
                    )
                }
                item(key = "qps") {
                    SettingParamRow(
                        label = "QPS",
                        desc = "网关全局限速（请求/分钟）。配合并行路数，防 429 风控拦截；默认 150，网络稳可升到 300–400。",
                        value = settings.qps,
                        onClick = { anchor = "qps" },
                    )
                }
                item(key = "burst") {
                    SettingParamRow(
                        label = "爆发",
                        desc = "单课时竞态爆发并发路数，用并发冗余加速进度累加；默认 8，过高易触发 WAF。",
                        value = settings.burst,
                        onClick = { anchor = "burst" },
                    )
                }
            }

            // 锚定式气泡弹窗：纵向数值列表 + 参数说明
            when (anchor) {
                "concurrency" -> SettingValuePopup(
                    title = "并行路数",
                    desc = "同时刷几个课时。越大越快，但过快易触发风控；默认 6，稳妥可降到 1–2。",
                    options = listOf("1", "2", "4", "6", "8", "12"),
                    selected = settings.concurrency,
                    onSelect = { onChange(settings.copy(concurrency = it)); anchor = null },
                    onDismiss = { anchor = null },
                )
                "qps" -> SettingValuePopup(
                    title = "QPS",
                    desc = "网关全局限速（请求/分钟）。配合并行路数，防 429 风控拦截；默认 150，网络稳可升到 300–400。",
                    options = listOf("50", "100", "150", "200", "300", "400"),
                    selected = settings.qps,
                    onSelect = { onChange(settings.copy(qps = it)); anchor = null },
                    onDismiss = { anchor = null },
                )
                "burst" -> SettingValuePopup(
                    title = "爆发",
                    desc = "单课时竞态爆发并发路数，用并发冗余加速进度累加；默认 8，过高易触发 WAF。",
                    options = listOf("4", "6", "8", "12", "16"),
                    selected = settings.burst,
                    onSelect = { onChange(settings.copy(burst = it)); anchor = null },
                    onDismiss = { anchor = null },
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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

/** 锚定式气泡：纵向平铺可选数值，顶部参数说明 */
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
                .width(240.dp)
                .padding(top = 120.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
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
                        )
                    }
                }
            }
        }
    }
}
