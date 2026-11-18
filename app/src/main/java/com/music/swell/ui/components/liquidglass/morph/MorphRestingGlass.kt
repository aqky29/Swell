package com.music.swell.ui.components.liquidglass.morph

import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.music.swell.ui.components.liquidglass.LiquidGlassBackdropState
import kotlin.math.min

/**
 * A capsule of glass at rest, drawn with the same material the account menu's morph uses.
 *
 * The reference draws its idle trigger with the very renderer that later morphs it, so the
 * pill and the droplet that grows out of it are one surface. This is that: the same shader,
 * rim and recipe, with no second shape fused in, filling the space it is given.
 *
 * The shader's rim is lit along the vertical axis (a key from above and a fill from below),
 * where the Kyant glass elsewhere in the app is lit on the diagonal.
 *
 * [frostDp] and [bodyArgb] are the resting glass's blur and body colour, which the app takes
 * from Settings → Liquid Glass; left alone it is the reference's 1dp frost and no body.
 *
 * Given a [press], the capsule is the reference's jelly control: it swells under a finger,
 * leans after a drag and takes a light that follows the touch.
 *
 * It draws into a layer a little larger than the space it is given, so the lens has backdrop
 * to bend in from beyond the edge, as the reference's does.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun MorphRestingGlass(
    backdropState: LiquidGlassBackdropState,
    isDark: Boolean,
    solid: Boolean,
    modifier: Modifier = Modifier,
    press: MorphPress? = null,
    frostDp: Float = 1f,
    bodyArgb: Int? = null,
) {
    val density = LocalDensity.current
    val renderer = remember(density.density) { MorphRenderers.resting(density.density) }
    val state = remember { MorphSurfaceState() }
    val margin = with(density) { RestingMargin.roundToPx() }
    // Where this space sits in the root; the backdrop is drawn from the root's origin.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rest = remember { RectF() }

    Box(modifier = modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val placeable = measurable.measure(
                        Constraints.fixed(width + 2 * margin, height + 2 * margin),
                    )
                    // Reports the space it was given, draws a margin beyond it.
                    layout(width, height) { placeable.place(-margin, -margin) }
                }
                .graphicsLayer {
                    val width = size.width
                    val height = size.height
                    if (width <= 1f || height <= 1f) {
                        renderEffect = null
                        return@graphicsLayer
                    }
                    val m = margin.toFloat()
                    // Under a finger the capsule swells and leans, and a light follows the touch.
                    rest.set(0f, 0f, width - 2f * m, height - 2f * m)
                    state.pressX = 0f
                    state.pressY = 0f
                    state.pressRadius = 1f
                    state.poolGain = 0f
                    state.pressLift = 0f
                    state.rimLift = 0f
                    if (press != null) {
                        press.tick
                        press.deform(rest, density.density)
                        if (!solid) press.light(state, m, m, density.density)
                    }
                    state.incoming.set(rest.left + m, rest.top + m, rest.right + m, rest.bottom + m)
                    state.source.set(state.incoming)
                    state.hasSource = false
                    val radius = min(state.incoming.width(), state.incoming.height()) / 2f
                    state.radiusX = radius
                    state.radiusY = radius
                    state.sourceRadius = radius
                    state.smoothing = 0f
                    state.lockedFieldStep = 0f
                    state.lensHeight = 0f
                    state.lensAmount = 0f
                    state.materialProgress = 0f
                    state.surfaceOpacity = 1f
                    state.dark = isDark
                    state.solid = solid
                    state.controlBlur = frostDp
                    state.hasControlTint = bodyArgb != null
                    state.controlTint = bodyArgb ?: 0
                    renderer.prepareResting(state)
                    renderEffect = renderer.materialEffect(state, 0f, 0f, width, height)
                        .asComposeRenderEffect()
                }
                .drawBehind {
                    @Suppress("UNUSED_VARIABLE")
                    val changed = backdropState.drawTick
                    if (solid) return@drawBehind
                    val bounds = backdropState.rootBounds
                    if (bounds.width <= 0f) return@drawBehind
                    translate(
                        left = bounds.left - (origin.x - margin),
                        top = bounds.top - (origin.y - margin),
                    ) {
                        drawLayer(backdropState.contentLayer)
                    }
                },
        )
    }
}

/** Backdrop beyond the capsule's edge for the lens to draw from; the reference uses the same. */
private val RestingMargin = 16.dp
