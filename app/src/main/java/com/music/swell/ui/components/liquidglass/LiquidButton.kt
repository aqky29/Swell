package com.music.swell.ui.components.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Warna surface default untuk varian Surface Liquid Button (Kyant0 catalog).
 * Menghasilkan rona milky frosted glass tipis yang menjaga kontras ikon/teks.
 */
fun surfaceLiquidColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.30f)

/**
 * Tombol Kaca Interaktif Kyant Liquid Glass (`LiquidButton`).
 *
 * Mengimplementasikan:
 * - Refraksi lensa sferis Kyant (`refractionHeight = 12dp`, `refractionAmount = 24dp`).
 * - Skala elastis & gesekan dinamis sentuhan:
 *   `scale = lerp(1f, 1f + 4dp / height, progress)`
 *   `translationX/Y = maxOffset * tanh(0.05 * offset / maxOffset)`
 * - Squash & stretch multi-arah sesuai sudut tarikan jari (`atan2`).
 * - Efek specular radial bloom (`InteractiveHighlight`).
 * - Dukungan `surfaceColor` (varian Surface Liquid Button di Kyant0) dan `tint`.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdropState: LiquidGlassBackdropState,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    glassMode: GlassEffectMode = GlassEffectMode.PREMIUM,
    isInteractive: Boolean = true,
    cornerRadius: Dp = 22.dp,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    /** Base glass to tune for a button (e.g. Swell's user-tuned pill); the chrome pill when null. */
    glassSettings: LiquidGlassSettings? = null,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val buttonSettings = remember(isDark, glassMode, glassSettings) {
        val base = glassSettings ?: ChromeGlass.chromePillGlass(isDark, glassMode)
        base.copy(
            refractionHeight = 12f,
            refractionAmount = 24f,
            // The pill's glass frosts as far as Settings says; only Kyant's lens wants it thin.
            blurRadiusDp = if (morphGlassAvailable(glassMode)) base.blurRadiusDp else 2f
        )
    }

    Box(
        modifier = modifier
            .then(
                if (isInteractive) {
                    Modifier.liquidPressTransform(interactiveHighlight)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        LiquidGlassBox(
            backdropState = backdropState,
            settings = buttonSettings,
            mode = glassMode,
            isDark = isDark,
            cornerRadius = cornerRadius,
            modifier = Modifier.matchParentSize()
        ) {
            if (tint.isSpecified) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.75f))
                        }
                )
            }
            if (surfaceColor.isSpecified) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(surfaceColor)
                )
            }
            // Glow tekan digambar di dalam kaca (ter-clip bentuk kapsul), bukan di Box luar,
            // agar tidak menyala sebagai persegi panjang penuh.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isInteractive) interactiveHighlight.modifier else Modifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * `LiquidButton`'s touch response on its own: swells +4dp under the finger and
 * leans toward it (tanh), squashing along the drag. For a control drawn inside
 * the page the glass samples, where a backdrop lens would sample itself; add
 * [InteractiveHighlight.modifier] after its fill for the glow.
 */
fun Modifier.liquidPressTransform(highlight: InteractiveHighlight): Modifier =
    graphicsLayer {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@graphicsLayer
        val progress = highlight.pressProgress
        val scale = lerp(1f, 1f + 4.dp.toPx() / height, progress)

        val maxOffset = size.minDimension
        val initialDerivative = 0.05f
        val offset = highlight.offset
        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

        val maxDragScale = 4.dp.toPx() / height
        val offsetAngle = atan2(offset.y, offset.x)
        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
    }.then(highlight.gestureModifier)

/** An [InteractiveHighlight] scoped to the caller, for [liquidPressTransform]. */
@Composable
fun rememberLiquidPress(): InteractiveHighlight {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }
}
