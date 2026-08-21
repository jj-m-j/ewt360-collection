package com.ewt.answer.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewt.answer.data.DebugLog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 调试模式二级页面：日志查看 / 分享 / 清空 */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf(DebugLog.readLog()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "调试模式",
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "EWT360 答案查询 v1.0.0 · 调试模式已开启",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "日志保存在 filesDir/logs/app.log（512KB 自动轮转）",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))

            // 日志显示区
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                Text(
                    text = logText.ifBlank { "(暂无日志，请先操作 App 复现问题)" },
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            Spacer(Modifier.height(16.dp))
            // 日志操作（等宽）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "刷新",
                    onClick = { logText = DebugLog.readLog() },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "分享",
                    onClick = { shareLog(context) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "清空",
                    onClick = {
                        DebugLog.clear()
                        logText = DebugLog.readLog()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun shareLog(context: android.content.Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EWT360 调试日志")
            putExtra(Intent.EXTRA_TEXT, DebugLog.readLog().takeLast(20000))
        }
        context.startActivity(Intent.createChooser(intent, "分享日志"))
    }
}
