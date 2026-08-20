package com.ewt.answer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液体玻璃悬浮底栏（按 backdrop 官方 Glass Bottom Bar 教程实现）：
 *
 * 效果参数对齐官方文档：
 * - effects: vibrancy() + blur(4dp) + lens(16dp, 32dp)
 * - shape:   CircleShape（胶囊）
 * - onDrawSurface: 半透明白（0.5）
 * - layerBlock: 按压缩放（backdrop 不缩放，仅内容缩放）
 *
 * 叠加液态选中滑块（catalog LiquidBottomTabs 思路）：
 * - Spring 位置动画 + 速度驱动拉伸（移动中拉长、到位回弹）
 * - 长按时色差折射/高光/投影/内阴影增强
 *
 * 兼容性：blur（Android 12+）、lens（Android 13+）自动降级为半透明 Surface。
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val density = LocalDensity.current
    val isLight = !isSystemInDarkTheme()

    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }

    // 液态滑块位置（0..tabs.size-1），Spring 移动 + 轻微回弹
    val selector = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        selector.animateTo(
            selectedIndex.toFloat(),
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }

    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .height(56.dp)
            .clip(CircleShape),
    ) {
        val tabWidth = maxWidth / tabs.size
        val tabWidthPx = with(density) { tabWidth.toPx() }

        // ── 1. 玻璃面板（官方效果参数） ──
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    // 按压缩放放 layerBlock：backdrop 不跟随缩放（官方 Interactive 教程）
                    layerBlock = {
                        val progress = highlight.pressProgress
                        val maxScale = (size.width + 16f.dp.toPx()) / size.width
                        val scale = lerp(1f, maxScale, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(surfaceColor.copy(alpha = 0.5f)) },
                )
                .then(highlight.modifier),
        )

        // ── 2. 液态选中滑块：位置 Spring + 速度拉伸 + 折射/高光/阴影（长按增强） ──
        Box(
            Modifier
                .fillMaxHeight()
                .width(tabWidth)
                .graphicsLayer {
                    translationX = selector.value * tabWidthPx
                    // 液态拉伸：移动速度越大拉得越长，到位自然回弹
                    val v = selector.velocity
                    val stretch = (v / tabWidthPx).coerceIn(-0.22f, 0.22f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.35f
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        val p = highlight.pressProgress
                        if (p > 0f) {
                            lens(16f.dp.toPx() * p, 32f.dp.toPx() * p, chromaticAberration = true)
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = highlight.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = highlight.pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * highlight.pressProgress,
                            alpha = highlight.pressProgress,
                        )
                    },
                    onDrawSurface = {
                        drawRect(
                            if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.06f),
                        )
                    },
                )
                .then(highlight.gestureModifier),
        )

        // ── 3. 文字内容（Miuix 风格，清晰前景） ──
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, label ->
                LiquidGlassTabItem(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}
