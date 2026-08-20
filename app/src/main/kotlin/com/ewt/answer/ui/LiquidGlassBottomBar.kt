package com.ewt.answer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
 * 液体玻璃悬浮底栏（紧凑悬浮胶囊）：
 *
 * 结构（严格参考官方 LiquidBottomTabs / Glass Bottom Bar 教程）：
 * 1. 玻璃面板：vibrancy + blur + lens 边缘折射 + 常态顶部高光 + 半透明 Surface
 * 2. 液态选中滑块：Spring 位置动画 + 速度驱动拉伸（移动中拉长、到位回弹）+
 *    长按时色差折射 / 高光 / 投影 / 内阴影增强
 * 3. 文字内容：Miuix 风格，选中 MIUI 蓝
 *
 * 宽度：内容包裹（Tab 文字 + 内边距），左右留 24dp，明显悬浮，非全宽。
 * 兼容：blur（Android 12+）、lens（Android 13+ AGSL）自动降级为半透明 Surface。
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val isLight = !isSystemInDarkTheme()
    // 官方 Demo 配色：accent 为 MIUI 蓝
    val accentColor = if (isLight) Color(0xFF0088FF) else Color(0xFF0091FF)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val density = LocalDensity.current

    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }

    // 液态滑块位置（0..tabs.size-1），官方 spring(0.5, 300, 0.001)
    val selector = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        selector.animateTo(
            selectedIndex.toFloat(),
            spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        )
    }

    // 底栏实际宽度（内容决定，onSizeChanged 记录）
    var barWidthPx by remember { mutableIntStateOf(0) }
    val tabWidthPx = if (tabs.isEmpty()) 0 else barWidthPx / tabs.size

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .height(56.dp)
            .clip(CircleShape)
            .onSizeChanged { barWidthPx = it.width },
    ) {
        // ── 1. 玻璃面板（官方效果参数，matchParentSize 精确铺满父） ──
        Box(
            Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(10f.dp.toPx())
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    // 常态玻璃顶部高光（柔和，官方 Highlight 思路）
                    highlight = {
                        Highlight.Default.copy(alpha = 0.35f)
                    },
                    // 按压缩放放 layerBlock：backdrop 不跟随缩放（官方 Interactive 教程）
                    layerBlock = {
                        val progress = highlight.pressProgress
                        val maxScale = (size.width + 16f.dp.toPx()) / size.width
                        val scale = lerp(1f, maxScale, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(surfaceColor.copy(alpha = 0.45f)) },
                )
                .then(highlight.modifier),
        )

        // ── 2. 液态选中滑块（对齐 CenterStart，初始位置在第一个 Tab） ──
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .height(56.dp)
                .width(with(density) { tabWidthPx.toDp() })
                .graphicsLayer {
                    translationX = selector.value * tabWidthPx
                    // 液态拉伸：移动速度越大拉得越长，到位自然回弹（克制）
                    val v = selector.velocity
                    val stretch = if (tabWidthPx > 0) (v / tabWidthPx).coerceIn(-0.22f, 0.22f) else 0f
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
                            if (isLight) Color.Black.copy(0.08f) else Color.White.copy(0.08f),
                        )
                    },
                )
                .then(highlight.gestureModifier),
        )

        // ── 3. 文字内容（决定底栏宽度；Miuix 风格） ──
        Row(Modifier.height(56.dp)) {
            tabs.forEachIndexed { index, label ->
                LiquidGlassTabItem(
                    text = label,
                    selected = index == selectedIndex,
                    accentColor = accentColor,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.padding(horizontal = 26.dp),
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassTabItem(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentColor else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}
