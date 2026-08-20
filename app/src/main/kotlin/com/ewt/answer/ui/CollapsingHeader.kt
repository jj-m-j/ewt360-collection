package com.ewt.answer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 基于列表滚动距离计算顶栏收缩进度（0=展开，1=完全收缩），连续插值非阈值切换。
 */
@Composable
fun rememberCollapseProgress(listState: LazyListState, collapseDistancePx: Float): State<Float> {
    return remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            if (first.index > 0) 1f else (-first.offset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }
}

/**
 * 液态玻璃滚动收缩顶栏（试卷 / 课程 / 设置 三页共用）：
 *
 * - LiquidGlass 材质与底部导航同语言：blur 10dp + surface 62%，完全保留
 * - 顶栏背景覆盖状态栏区域（Edge-to-Edge 下从屏幕顶部渲染，玻璃延伸到状态栏后面）
 * - 大标题展开态左侧 16dp + 副标题下方左对齐 → 滚动时标题缩放并水平居中、副标题淡出
 * - 标题始终处于（状态栏 + 顶栏）整体垂直中心，上下等边距
 * - 右侧 actions 保留
 */
@Composable
fun CollapsingHeaderBar(
    title: String,
    subtitle: String?,
    progress: Float,
    backdrop: LayerBackdrop,
    glassSurface: Color,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 展开态含状态栏区域，收缩态紧凑；标题垂直居中保证上下等边距
    val headerHeight = lerp(96.dp, 54.dp, progress)
    val titleSize = lerp(26.sp, 17.sp, progress)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            // LiquidGlass 材质（与底部同源，完全保留）：背景覆盖到屏幕顶部（含状态栏）
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(10f.dp.toPx()) },
                onDrawSurface = {
                    drawRect(glassSurface.copy(alpha = 0.62f))
                },
            ),
    ) {
        val containerW = with(density) { maxWidth.toPx() }
        val offset16Px = with(density) { 16.dp.toPx() }
        val offset8Px = with(density) { 8.dp.toPx() }
        val gapPx = with(density) { 4.dp.toPx() }
        var titleW by remember { mutableIntStateOf(0) }
        var titleH by remember { mutableIntStateOf(0) }

        // 主标题：始终垂直居中（上下等边距）；水平从左侧 16dp 平滑移到居中
        Text(
            text = title,
            fontSize = titleSize,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .onSizeChanged {
                    titleW = it.width
                    titleH = it.height
                }
                .graphicsLayer {
                    translationX = -(containerW / 2f - offset16Px - titleW / 2f) * (1f - progress)
                },
        )

        // 副标题：初始与主标题左对齐（左侧 16dp），随滚动淡出 + 上移 + 轻微缩小（不占位）
        if (subtitle != null) {
            var subW by remember { mutableIntStateOf(0) }
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .onSizeChanged { subW = it.width }
                    .graphicsLayer {
                        // 水平与主标题同规则左对齐；垂直在标题下方
                        translationX = -(containerW / 2f - offset16Px - subW / 2f) * (1f - progress)
                        translationY = titleH / 2f + gapPx * (1f - progress) - offset8Px * progress
                        alpha = 1f - progress
                        scaleX = 1f - 0.04f * progress
                        scaleY = 1f - 0.04f * progress
                    },
            )
        }

        // 右侧 actions（如课程页设置图标）
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}
