package com.music.swell.ui.components.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Satu slot [LiquidGlassInlineTabBar]: ikon atau label teks.
 *
 * @param selectable `false` untuk tombol aksi (mis. toggle sidebar): diketuk memanggil
 *   callback tanpa memindahkan indikator, dan indikator yang dilepas di atasnya kembali.
 */
@Immutable
data class InlineGlassTab(
    val label: String? = null,
    val icon: ImageVector? = null,
    val contentDescription: String? = label,
    val selectable: Boolean = true
)

/**
 * Padanan `GlassTabBar.inline` pada navigasi atas tablet MusikKu, dengan efek navbar HP yang
 * sama (`MorphingGlassTabsCapsule`): indikator lensa kaca yang dapat ditekan & digeser,
 * jelly squash & stretch, panel bergeser 4dp, kapsul membesar 16dp selama lensa aktif, dan
 * slot beraksen yang hanya terlihat di bawah lensa.
 *
 * @param selectedIndex slot terpilih; `null` menyembunyikan indikator.
 */
@Composable
fun LiquidGlassInlineTabBar(
    tabs: List<InlineGlassTab>,
    selectedIndex: Int?,
    onTabSelected: (Int) -> Unit,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState,
    glassSettings: LiquidGlassSettings,
    glassMode: GlassEffectMode,
    modifier: Modifier = Modifier,
    barHeight: Dp = 56.dp,
    accentColor: Color = if (isDark) Color(0xFF0091FF) else Color(0xFF0088FF)
) {
    val tabCount = tabs.size.coerceAtLeast(1)
    val density = LocalDensity.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentTabs by rememberUpdatedState(tabs)
    val restingIndex = (selectedIndex ?: 0).coerceIn(0, tabCount - 1)
    val currentRestingIndex by rememberUpdatedState(restingIndex)
    val contentColor = if (isDark) Color.White else Color.Black

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex != null) 1f else 0f,
        animationSpec = tween(180),
        label = "inlineIndicatorAlpha"
    )

    val tabsLayer = rememberGraphicsLayer()
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    // Satu blur latar untuk kapsul dan baris beraksen, lihat SharedGlassBackdrop. Margin memberi
    // ruang bagi kapsul yang membesar saat lensa ditekan.
    val sharedBackdrop = rememberSharedGlassBackdrop(
        backdropState = backdropState,
        blurRadius = glassSettings.blurRadiusDp.dp,
        enabled = glassMode != GlassEffectMode.SOLID,
        saturation = glassSettings.backdropSaturation(glassMode)
    )
    val sharedRegionHeightPx = with(density) { barHeight.toPx() }
    val sharedRegionMarginPx = with(density) { 24.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .height(barHeight)
            .sharedGlassBackdropSource(
                shared = sharedBackdrop,
                regionHeightPx = sharedRegionHeightPx,
                marginPx = sharedRegionMarginPx
            )
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = with(density) { barHeight.toPx() }
        val pad4Px = with(density) { 4.dp.toPx() }
        val tabWidthPx = ((widthPx - pad4Px * 2f) / tabCount).coerceAtLeast(1f)
        val tabWidthState = rememberUpdatedState(tabWidthPx)
        val widthState = rememberUpdatedState(widthPx)
        val rowHeight = (barHeight - 8.dp).coerceAtLeast(1.dp)
        val rowHeightPx = with(density) { rowHeight.toPx() }

        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / widthState.value.coerceAtLeast(1f)).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() } * sign(fraction) * EaseOut.transform(abs(fraction))
            }
        }

        var currentIndex by remember { mutableIntStateOf(restingIndex) }
        val dampedDragAnimation = remember(animationScope, tabCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = restingIndex.toFloat(),
                valueRange = 0f..(tabCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                    // Slot aksi tidak bisa jadi pilihan: indikator kembali ke slot terpilih.
                    val snapTo = if (currentTabs.getOrNull(target)?.selectable == true) target else currentRestingIndex
                    currentIndex = snapTo
                    animateToValue(snapTo.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthState.value)
                            .coerceIn(0f, (tabCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(restingIndex) {
            currentIndex = restingIndex
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    if (index != currentRestingIndex) currentOnTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        (dampedDragAnimation.value + 0.5f) * tabWidthState.value + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }
        val expandPx = with(density) { 16.dp.toPx() }

        // ── 1. Kapsul kaca utama ──
        LiquidGlassBox(
            backdropState = backdropState,
            settings = glassSettings,
            mode = glassMode,
            isDark = isDark,
            cornerRadius = barHeight / 2,
            sharedBackdrop = sharedBackdrop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panelOffset
                    val scale = lerp(1f, 1f + expandPx / size.width.coerceAtLeast(1f), dampedDragAnimation.pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(interactiveHighlight.modifier)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    InlineTabCell(
                        tab = tab,
                        color = contentColor,
                        onClick = {
                            if (tab.selectable) currentIndex = index else currentOnTabSelected(index)
                        }
                    )
                }
            }
        }

        // ── 2. Baris beraksen tak terlihat, direkam ke tabsLayer dan hanya tampil di bawah lensa ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(rowHeight)
                .clearAndSetSemantics {}
                .graphicsLayer { translationX = panelOffset }
                .drawWithContent {
                    tabsLayer.record { this@drawWithContent.drawContent() }
                }
        ) {
            LiquidGlassBox(
                backdropState = backdropState,
                settings = glassSettings.copy(depthEffect = false, innerShadowAlpha = 0f, drawSpecularRim = false),
                mode = glassMode,
                isDark = isDark,
                cornerRadius = rowHeight / 2,
                refractionProgress = { dampedDragAnimation.pressProgress },
                sharedBackdrop = sharedBackdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .kyantHighlight(alpha = { dampedDragAnimation.pressProgress })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(interactiveHighlight.modifier)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        InlineTabCell(
                            tab = tab,
                            color = accentColor,
                            onClick = null,
                            scale = { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }
                        )
                    }
                }
            }
        }

        // ── 3. Indikator lensa kaca yang dapat ditekan & digeser ──
        if (indicatorAlpha > 0.01f) {
            LiquidGlassTabIndicator(
                backdropState = backdropState,
                tabsLayer = tabsLayer,
                groupOriginInRoot = { originInRoot },
                tabsLayerOffset = { Offset(panelOffset, (heightPx - rowHeightPx) / 2f) },
                translation = {
                    Offset(
                        pad4Px + dampedDragAnimation.value * tabWidthState.value + panelOffset,
                        (heightPx - rowHeightPx) / 2f
                    )
                },
                scaleX = {
                    val velocity = dampedDragAnimation.velocity / 10f
                    dampedDragAnimation.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                },
                scaleY = {
                    val velocity = dampedDragAnimation.velocity / 10f
                    dampedDragAnimation.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                },
                pressProgress = { dampedDragAnimation.pressProgress },
                isDark = isDark,
                mode = glassMode,
                modifier = Modifier
                    .graphicsLayer { alpha = indicatorAlpha }
                    .then(if (selectedIndex != null) interactiveHighlight.gestureModifier else Modifier)
                    .then(if (selectedIndex != null) dampedDragAnimation.modifier else Modifier)
                    .size(
                        width = with(density) { tabWidthPx.toDp() },
                        height = rowHeight
                    )
            )
        }
    }
}

@Composable
private fun RowScope.InlineTabCell(
    tab: InlineGlassTab,
    color: Color,
    onClick: (() -> Unit)?,
    scale: () -> Float = { 1f }
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        role = if (tab.selectable) Role.Tab else Role.Button,
                        onClickLabel = tab.contentDescription,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center
    ) {
        if (tab.icon != null) {
            Icon(
                imageVector = tab.icon,
                contentDescription = if (onClick != null) tab.contentDescription else null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = tab.label.orEmpty(),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
