package com.music.swell.ui.components.liquidglass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.swell.data.settings.AppSettings

/**
 * The glass every Swell surface draws with: the Premium/Solid choice and the
 * user's tuning from Settings → Liquid Glass, already folded into the bar and
 * pill parameters so a surface only has to pick which one it is.
 */
@Immutable
data class SwellGlass(
    val mode: GlassEffectMode,
    val isDark: Boolean,
    val bar: LiquidGlassSettings,
    val pill: LiquidGlassSettings,
)

val LocalSwellGlass = compositionLocalOf<SwellGlass?> { null }

/** Built once at the app root from [AppSettings]; see [LocalSwellGlass]. */
@Composable
fun rememberSwellGlass(isDark: Boolean): SwellGlass {
    val solid by AppSettings.glassSolid.collectAsStateWithLifecycle()
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val thickness by AppSettings.glassThickness.collectAsStateWithLifecycle()
    val refractiveIndex by AppSettings.glassRefractiveIndex.collectAsStateWithLifecycle()
    val blur by AppSettings.glassBlur.collectAsStateWithLifecycle()
    val tinted by AppSettings.glassTinted.collectAsStateWithLifecycle()
    val opacity by AppSettings.glassOpacity.collectAsStateWithLifecycle()
    val blurEnabled by AppSettings.glassBlurEnabled.collectAsStateWithLifecycle()
    val lightAngle by AppSettings.glassLightAngle.collectAsStateWithLifecycle()
    return remember(isDark, solid, reduceBlur, thickness, refractiveIndex, blur, tinted, opacity, blurEnabled, lightAngle) {
        val mode = if (solid || reduceBlur) GlassEffectMode.SOLID else GlassEffectMode.PREMIUM
        val body = GlassBody.of(isDark, tinted, opacity)
        val frost = if (blurEnabled) blur else 0f
        SwellGlass(
            mode = mode,
            isDark = isDark,
            bar = ChromeGlass.chromeBarGlass(isDark, mode).tuned(mode, thickness, refractiveIndex, frost, body, lightAngle),
            pill = ChromeGlass.chromePillGlass(isDark, mode).tuned(mode, thickness, refractiveIndex, frost, body, lightAngle),
        )
    }
}

/**
 * iOS 26's Clear and Tinted glass, the choice Music OS 26 offers under
 * Settings → Liquid Glass. Clear keeps the body nearly see-through; Tinted
 * lays a denser body over it so labels on the glass hold their contrast.
 * Tinted's saturation and the light body come from liquid_glass_widgets'
 * `ios27Light`/`ios27Dark`, measured against the native control (light
 * 0x87F8F8F8 at saturation 2.1, dark at 1.4).
 */
internal data class GlassBody(val tintArgb: Long, val vibrancy: Float) {
    companion object {
        fun of(isDark: Boolean, tinted: Boolean, opacity: Float): GlassBody {
            val t = opacity.coerceIn(0f, 1f)
            val (rgb, range) = when {
                !tinted && !isDark -> 0xFAFAFAL to (0.08f..0.48f)
                // Lighter than the page, never darker: a thin white body (ios27Dark is
                // 0x1FFFFFFF, ~12%) lets the page's own colour carry through, where a
                // black one turns every control into a dark hole.
                !tinted && isDark -> 0xFFFFFFL to (0.05f..0.21f)
                tinted && !isDark -> 0xF8F8F8L to (0.45f..0.85f)
                else -> 0x3A3A3CL to (0.40f..0.78f)
            }
            val alpha = range.start + (range.endInclusive - range.start) * t
            val a = (alpha * 255f).toLong().coerceIn(0L, 255L)
            // A dark page has little colour to lift; push it harder there.
            val vibrancy = when {
                tinted && isDark -> 1.6f
                tinted -> 2.1f
                isDark -> 1.9f
                else -> 1.5f
            }
            return GlassBody((a shl 24) or rgb, vibrancy)
        }
    }
}

/** The app's [SwellGlass], or one built from the current theme outside the app root (previews). */
@Composable
fun currentSwellGlass(): SwellGlass =
    LocalSwellGlass.current ?: rememberSwellGlass(MaterialTheme.colorScheme.background.luminance() < 0.5f)

/**
 * Applies the Settings sliders the same way the liquid_glass_navbar reference
 * does: thickness is the lens band, the IOR sets how far it bends (Kyant0's
 * `(ior - 1) * 120` dp), and blur is the backdrop sigma. [lightAngleDeg] turns the
 * specular rim. Solid ignores them.
 */
private fun LiquidGlassSettings.tuned(
    mode: GlassEffectMode,
    thickness: Float,
    refractiveIndex: Float,
    blur: Float,
    body: GlassBody,
    lightAngleDeg: Float,
): LiquidGlassSettings =
    if (mode == GlassEffectMode.SOLID) {
        this
    } else {
        copy(
            thickness = thickness,
            refractiveIndex = refractiveIndex,
            blurRadiusDp = blur,
            refractionHeight = thickness,
            refractionAmount = (refractiveIndex - 1f) * 120f,
            surfaceTintArgb = body.tintArgb,
            vibrancy = body.vibrancy,
            highlightAngleDeg = lightAngleDeg,
        )
    }
