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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
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
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 液体玻璃底栏参数（克制取值，避免过度效果） */
private object GlassDefaults {
    /** 左右留距（悬浮感） */
    val OutsidePadding = 24.dp
    /** 上下留距 */
    val VerticalPadding = 10.dp
    /** 底栏高度（对齐 miuix FloatingNavigationBar 52dp） */
    val BarHeight = 52.dp
    /** 胶囊圆角（miuix 风格大圆角） */
    val CornerRadius = 50.dp
    /** 模糊半径 dp */
    const val BlurRadiusDp = 14f
    /** 折射高度 dp（边缘折射范围） */
    const val LensHeightDp = 12f
    /** 折射强度 dp */
    const val LensAmountDp = 10f
    /** 玻璃 Surface 不透明度 */
    const val SurfaceAlpha = 0.38f
    /** 常态顶部高光强度 */
    const val HighlightAlpha = 0.25f
}

/**
 * 液体玻璃悬浮底栏（借鉴 AndroidLiquidGlass 的 Backdrop/Lens/Highlight 原理，自行实现）：
 *
 * 三层结构：
 * 1. 玻璃面板：实时 Backdrop Blur + 轻微 Lens 折射（边缘采样偏移）+ 顶部高光 + 半透明 Surface
 * 2. 液态选中滑块：Spring 位置动画 + 速度驱动拉伸（移动中拉长、到位回弹）+
 *    长按时色差折射/高光/投影/内阴影增强
 * 3. 文字内容：Miuix 风格，保持清晰
 *
 * 兼容性：blur（Android 12+ RenderEffect）、lens（Android 13+ RuntimeShader）
 * 低版本自动降级为半透明 Surface + 高光。
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
    val cornerPx = with(density) { GlassDefaults.CornerRadius.toPx() }
    val blurPx = with(density) { GlassDefaults.BlurRadiusDp.dp.toPx() }
    val lensHeightPx = with(density) { GlassDefaults.LensHeightDp.dp.toPx() }
    val lensAmountPx = with(density) { GlassDefaults.LensAmountDp.dp.toPx() }
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
            .padding(
                horizontal = GlassDefaults.OutsidePadding,
                vertical = GlassDefaults.VerticalPadding,
            )
            .shadow(6.dp, RoundedCornerShape(GlassDefaults.CornerRadius), clip = false)
            .height(GlassDefaults.BarHeight)
            .clip(RoundedCornerShape(GlassDefaults.CornerRadius)),
    ) {
        val tabWidth = maxWidth / tabs.size
        val tabWidthPx = with(density) { tabWidth.toPx() }

        // ── 1. 玻璃面板：Blur + Lens + 顶部高光 + 半透明 Surface + 长按光斑 ──
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(GlassDefaults.CornerRadius) },
                    effects = {
                        blur(blurPx)
                        lens(lensHeightPx, lensAmountPx)
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = GlassDefaults.HighlightAlpha)
                    },
                    layerBlock = {
                        // 长按：轻微放大（克制）
                        val p = highlight.pressProgress
                        val s = lerp(1f, 1.04f, p)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = {
                        drawRoundRect(
                            color = surfaceColor.copy(alpha = GlassDefaults.SurfaceAlpha),
                            cornerRadius = CornerRadius(cornerPx),
                        )
                    },
                )
                .then(highlight.modifier),
        )

        // ── 2. 液态选中滑块：Spring 位置 + 速度拉伸 + 折射/高光/阴影（长按增强） ──
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
                    shape = { RoundedCornerShape(GlassDefaults.CornerRadius) },
                    effects = {
                        val p = highlight.pressProgress
                        if (p > 0f) {
                            lens(blurPx * p, blurPx * p, chromaticAberration = true)
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
                        drawRoundRect(
                            color = if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.06f),
                            cornerRadius = CornerRadius(cornerPx),
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
