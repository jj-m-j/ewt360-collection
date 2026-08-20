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

/** 单个 Tab 单元宽度（固定，避免动态测量导致滑块越界/卡住） */
private val LiquidGlassTabWidth = 88.dp

/** 胶囊总宽 = 单元宽 × N + 两侧 4dp 内边距（固定值，几何确定） */
private fun capsuleWidth(tabsCount: Int) = LiquidGlassTabWidth * tabsCount + 8.dp

/**
 * 液态玻璃悬浮底栏（官方 LiquidBottomTabs / miuix 示例同款交互，内容包裹窄胶囊）：
 *
 * 1. 玻璃面板：真实 backdrop 采样（vibrancy 增色 + blur 实时模糊 + lens 边缘折射），
 *    常态保持顶部玻璃高光，按压时整条面板轻微放大
 * 2. 液态选中内胆：纯显示层，位置由 DampedDragAnimation 驱动，固定单元宽（88dp）
 *    保证滑块永不出界；移动中按速度拉伸、到位回弹，按压时色差折射 + 高光 + 阴影
 * 3. 交互：面板整条即输入层（官方模型，不用 clickable）—— 点击 = 快速 down/up，
 *    由 onDragStarted 定位 + onDragStopped 提交；拖动 = 跟手 + 惯性回弹
 * 4. 内胆内容：捕获自"不可见"的 accent 层（图标+文字整体染成 MIUI 蓝，无任何输入修饰），
 *    玻璃折射出选中 Tab 的内容，形成 HyperOS 液态玻璃标志性效果
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

    // 固定几何：胶囊总宽（px）与单格宽（px）——不依赖测量，滑块永不出界
    val capsuleWidthPx = with(density) { capsuleWidth(tabsCount).toPx() }
    val tabWidthPx = with(density) { LiquidGlassTabWidth.toPx() }
    val tabWidthPxState = rememberUpdatedState(tabWidthPx)
    val onTabSelectedState = rememberUpdatedState(onTabSelected)

    /** 触摸点 x → 所在 Tab 索引（点击定位用） */
    fun indexAt(positionX: Float): Int {
        val tabWidthPx = tabWidthPxState.value
        if (tabWidthPx <= 0f) return 0
        return (positionX / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
    }

    var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }

    // 整条面板随拖动轻微位移（官方 4dp 液态偏移）
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4f.dp.toPx() }
    val panelOffset by remember(rubberBandPx, capsuleWidthPx) {
        derivedStateOf {
            if (capsuleWidthPx <= 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / capsuleWidthPx).fastCoerceIn(-1f, 1f)
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
                updateValue(targetIndex.toFloat())
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
                .width(capsuleWidth(tabsCount))
                .height(64f.dp)
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
                    .width(capsuleWidth(tabsCount))
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
                .width(LiquidGlassTabWidth),
        )
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

/** 设置（齿轮，material settings） */
internal val SettingsTabIcon: ImageVector by lazy {
    iconVector(
        name = "Settings",
        pathData = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    )
}

/** 课程（毕业帽，material school） */
internal val CourseTabIcon: ImageVector by lazy {
    iconVector(
        name = "Course",
        pathData = "M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6l9,-4.91V17h2V9L12,3z",
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
