package com.ewt.answer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import top.yukonga.miuix.kmp.basic.Text

/** 底栏单个 Tab：图标 + 文字 */
data class LiquidGlassTab(
    val icon: ImageVector,
    val label: String,
)

/** Tab 内容按压放大系数（仅作用于不可见 accent 层） */
internal val LiquidGlassTabScale =
    staticCompositionLocalOf { { 1f } }

/** 单个 Tab 单元宽度（内容包裹，固定单元，窄胶囊） */
private val LiquidGlassTabWidth = 88.dp

/**
 * 液态玻璃悬浮底栏（官方 LiquidBottomTabs / miuix 示例同款交互，内容包裹窄胶囊）：
 *
 * 1. 玻璃面板：真实 backdrop 采样（vibrancy 增色 + blur 实时模糊 + lens 边缘折射），
 *    常态保持顶部玻璃高光，按压时整条面板轻微放大
 * 2. 液态选中内胆：纯显示层，位置由 DampedDragAnimation 驱动，移动中按速度拉伸、
 *    到位回弹；长按/按压时色差折射 + 高光 + 投影 + 内阴影
 * 3. 交互：面板整条即输入层（官方模型，不用 clickable）—— 点击 = 快速 down/up，
 *    由 onDragStarted 定位 + onDragStopped 提交；拖动 = 跟手 + 惯性回弹
 * 4. 内胆内容：捕获自"不可见"的 accent 层（图标+文字整体染成 MIUI 蓝，无任何输入修饰，
 *    不拦截点击），玻璃折射出选中 Tab 的内容，形成 HyperOS 液态玻璃标志性效果
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<LiquidGlassTab>,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val contentColor = if (isLightTheme) Color(0xFF252525) else Color(0xFFD6D6D6)
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.42f)
        else Color(0xFF121212).copy(alpha = 0.42f)
    val tabsCount = tabs.size.coerceAtLeast(1)

    // 捕获不可见 accent 层（选中内容染蓝）供内胆折射
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    // 面板实测宽度（内容包裹）→ 单格宽度
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val tabWidthPx = if (totalWidthPx > 0f) (totalWidthPx / tabsCount).coerceAtLeast(0f) else 0f
    // 闭包内动态读取（remember 创建的 lambda 不会捕获旧值）
    val tabWidthPxState = rememberUpdatedState(tabWidthPx)
    val totalWidthPxState = rememberUpdatedState(totalWidthPx)
    val onTabSelectedState = rememberUpdatedState(onTabSelected)

    /** 触摸点 x → 所在 Tab 索引（点击定位用） */
    fun indexAt(positionX: Float): Int {
        val tabWidthPx = tabWidthPxState.value
        if (tabWidthPx <= 0f) return 0
        val logicalX = if (isLtr) positionX else totalWidthPxState.value - positionX
        return (logicalX / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
    }

    var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }

    // 整条面板随拖动轻微位移（官方 4dp 液态偏移）
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4f.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            val totalWidthPx = totalWidthPxState.value
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    val dampedDragAnimation = remember(animationScope, tabsCount) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedTabIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            // 点击/拖拽起点：先定位到落点所在 Tab
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            // 抬手提交：就近取整 → 切 Tab
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onTabSelectedState.value(targetIndex)
                }
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(
                        0f,
                        spring(1f, 300f, 0.5f),
                    )
                }
            },
            // 拖动跟手
            onDrag = { _, dragAmount ->
                val tabWidthPx = tabWidthPxState.value
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }
    // 外部选中变化（如从设置页返回）→ 同步内胆位置
    LaunchedEffect(selectedTabIndex) {
        snapshotFlow { selectedTabIndex() }
            .collectLatest { index ->
                if (currentIndex != index) {
                    currentIndex = index
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
            }
    }

    // 高光光斑跟随液态内胆
    val interactiveHighlight = remember(animationScope, dampedDragAnimation) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDragAnimation.value + 0.5f) * tabWidthPxState.value + panelOffset
                    } else {
                        layerSize.width - (dampedDragAnimation.value + 0.5f) * tabWidthPxState.value + panelOffset
                    },
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        // ── 1. 玻璃面板 + 可见 Tab 内容（也是整条输入层：点击/拖拽） ──
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(10f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    // 常态玻璃顶部高光（柔和）
                    highlight = {
                        Highlight.Default.copy(alpha = 0.3f)
                    },
                    // 按压缩放：backdrop 不跟随缩放（官方方案）
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .height(64f.dp)
                .onSizeChanged { totalWidthPx = it.width.toFloat() }
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassTabRowContent(
                tabs = tabs,
                selectedIndex = currentIndex,
                contentColor = contentColor,
                accentColor = accentColor,
                onTabSelected = onTabSelectedState.value,
            )
        }

        // ── 2. 不可见 accent 层：图标+文字整体染成 MIUI 蓝，捕获进 tabsBackdrop ──
        // 纯显示、无任何输入修饰 → 不拦截点击
        CompositionLocalProvider(
            LiquidGlassTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .height(56f.dp)
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassTabRowContent(
                    tabs = tabs,
                    selectedIndex = currentIndex,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onTabSelected = onTabSelectedState.value,
                )
            }
        }

        // ── 3. 液态选中内胆（纯显示：combined backdrop = 屏幕内容 + accent 层折射） ──
        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX =
                            if (isLtr) progressOffset + panelOffset
                            else -progressOffset + panelOffset
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        shadow = {
                            Shadow(alpha = dampedDragAnimation.pressProgress)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8f.dp * dampedDragAnimation.pressProgress,
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        },
                        // 液态拉伸：速度越大内胆越扁长，到位回弹
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                if (isLightTheme) Color.Black.copy(alpha = 0.08f)
                                else Color.White.copy(alpha = 0.08f),
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    )
                    .height(56f.dp)
                    .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}

/** Tab 行内容（图标 + 文字），普通层与 accent 层共用 */
@Composable
internal fun RowScope.LiquidGlassTabRowContent(
    tabs: List<LiquidGlassTab>,
    selectedIndex: Int,
    contentColor: Color,
    accentColor: Color,
    onTabSelected: (Int) -> Unit,
) {
    tabs.forEachIndexed { index, tab ->
        LiquidGlassTabItem(
            tab = tab,
            selected = index == selectedIndex,
            contentColor = contentColor,
            accentColor = accentColor,
            onTabSelected = onTabSelected,
            index = index,
        )
    }
}

/** 单个 Tab：图标 + 文字（无 clickable，点击由面板输入层统一处理；仅保留无障碍语义） */
@Composable
internal fun RowScope.LiquidGlassTabItem(
    tab: LiquidGlassTab,
    selected: Boolean,
    contentColor: Color,
    accentColor: Color,
    onTabSelected: (Int) -> Unit,
    index: Int,
) {
    val tabScale = LiquidGlassTabScale.current
    Column(
        Modifier
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
                onClick {
                    onTabSelected(index)
                    true
                }
            }
            .width(LiquidGlassTabWidth)
            .fillMaxHeight()
            .graphicsLayer {
                val s = tabScale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = rememberVectorPainter(tab.icon),
            contentDescription = tab.label,
            modifier = Modifier.size(26f.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Text(
            text = tab.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentColor else contentColor,
        )
    }
}

// ── 图标（material 风格 outline） ──

internal val PaperTabIcon: ImageVector by lazy {
    iconVector(
        name = "Paper",
        pathData = "M14,2L6,2C4.9,2 4,2.9 4,4L4,20C4,21.1 4.9,22 6,22L18,22C19.1," +
            "22 20,21.1 20,20L20,8L14,2ZM16,18L8,18L8,16L16,16L16,18ZM16,14L8,14L8,12L16,12L16," +
            "14ZM13,9L13,3.5L18.5,9L13,9Z",
    )
}

internal val AboutTabIcon: ImageVector by lazy {
    iconVector(
        name = "About",
        pathData = "M12,2C6.48,2 2,6.48 2,12C2,17.52 6.48,22 12,22C17.52,22 22,17.52 22,12C22," +
            "6.48 17.52,2 12,2ZM13,17L11,17L11,11L13,11L13,17ZM13,9L11,9L11,7L13,7L13,9Z",
    )
}

private fun iconVector(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()
