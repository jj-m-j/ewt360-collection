package com.ewt.answer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
 * 液态玻璃滚动收缩顶栏（试卷列表 / 课程页共用同一套设计逻辑）：
 *
 * - LiquidGlass 材质与底部导航同语言：blur 10dp + surface 62%，完全保留既有实现
 * - 顶栏整体位于状态栏下方（statusBarsPadding），标题不贴状态栏
 * - 标题始终处于顶栏垂直视觉中心（上下等边距），展开态左侧 16dp → 收缩态水平居中
 * - 标题字号 26sp → 17sp 连续缩放
 * - 副标题（如"你好，xx"）随滚动淡出 + 上移 8dp + 轻微缩小，不占位
 * - 右侧 actions 保留（如课程页设置图标）
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
    val headerHeight = lerp(96.dp, 54.dp, progress)
    val titleSize = lerp(26.sp, 17.sp, progress)
    val density = LocalDensity.current

    // 顶栏整体位于状态栏下方（含安全区域），标题不贴近状态栏
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                // LiquidGlass 材质（与底部同源，完全保留）
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(10f.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(glassSurface.copy(alpha = 0.62f))
                    },
                ),
        ) {
            // px 换算全部在 composable 作用域预计算（miuix Surface 同款写法）
            val containerW = with(density) { maxWidth.toPx() }
            val offset16Px = with(density) { 16.dp.toPx() }
            val offset8Px = with(density) { 8.dp.toPx() }
            var titleW by remember { mutableIntStateOf(0) }
            var titleH by remember { mutableIntStateOf(0) }

            // 标题：始终垂直居中（上下等边距）；水平从左侧 16dp 平滑移到中心
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
                        // progress=0 左移（到 16dp 处），progress=1 居中
                        translationX = -(containerW / 2f - offset16Px - titleW / 2f) * (1f - progress)
                    },
            )

            // 副标题：标题下方，随滚动淡出 + 上移 + 轻微缩小（不占位）
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = 1f - progress
                            translationY = titleH / 2f + (1f - progress) * with(density) { 4.dp.toPx() } - offset8Px * progress
                            scaleX = 1f - 0.04f * progress
                            scaleY = 1f - 0.04f * progress
                        },
                )
            }

            // 右侧 actions（如课程页设置图标）
            if (actions != null) {
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
    }
}
