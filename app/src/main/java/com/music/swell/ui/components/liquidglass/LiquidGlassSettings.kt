package com.music.swell.ui.components.liquidglass

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Mode efek kaca Swell. Tidak ada mode "ringan": kaca selalu penuh (Premium) atau dimatikan (Solid).
 * - [PREMIUM]: Shader AGSL Kyant (refraksi bola circleMap + 7-spectral chromatic dispersion + AGSL highlight + inner shadow).
 * - [SOLID]: Permukaan pekat (#1C1C1E gelap / #F2F2F7 terang) tanpa efek refraksi/blur.
 */
enum class GlassEffectMode {
    PREMIUM,
    SOLID
}

/**
 * Parameter optik kaca cair Kyant Liquid Glass (`io.github.kyant0:backdrop`).
 */
data class LiquidGlassSettings(
    val glassColorArgb: Long,
    val thickness: Float = 24f,
    val blurRadiusDp: Float = 8f,
    val chromaticAberration: Float = 0.015f,
    val lightAngleRadians: Float = DEFAULT_LIGHT_ANGLE_RAD,
    val lightIntensity: Float = 0.50f,
    val ambientStrength: Float = 0f,
    val refractiveIndex: Float = 1.2f,
    val fresnelStrength: Float = 0.0f,
    val saturation: Float = 1.5f,
    val drawSpecularRim: Boolean = true,
    // Parameter optik khas Kyant0:
    val refractionHeight: Float = 24f,
    val refractionAmount: Float = 24f,
    val depthEffect: Boolean = true,
    val innerShadowRadiusDp: Float = 8f,
    val innerShadowAlpha: Float = 0.35f,
    val highlightAlpha: Float = 0.70f,
    val highlightFalloff: Float = 1.0f,
    val highlightAngleDeg: Float = DEFAULT_KYANT_HIGHLIGHT_ANGLE_DEG,
    /** `true` = garis tepi gaya `Highlight.Default` Kyant0 (0.5dp, blur, diklip, putih 50%). */
    val useKyantHighlight: Boolean = false,
    /** `true` = bayangan jatuh `Shadow.Default` Kyant0 (radius 24dp, offset y 4dp, hitam 10%). */
    val dropShadow: Boolean = false,
    /**
     * Warna lapisan tubuh kaca di atas latar (gaya Clear/Tinted iOS 26). `null` memakai
     * tint bawaan Kyant0, lihat [glassContainerColor].
     */
    val surfaceTintArgb: Long? = null,
    /** Saturasi latar di bawah kaca (vibrancy). iOS 27 terukur: 2.1 terang, 1.4 gelap. */
    val vibrancy: Float = 1.5f
) {
    val alphaFraction: Float
        get() = ((glassColorArgb ushr 24) and 0xFFL).toFloat() / 255f

    companion object {
        /** 135 derajat (arah cahaya dari kiri-atas, sama dengan orientasi natural Apple & Kyant). */
        const val DEFAULT_LIGHT_ANGLE_RAD: Float = 2.3561945f // 3 * PI / 4
        const val DEFAULT_KYANT_HIGHLIGHT_ANGLE_DEG: Float = 135f

        /** Lebar pita tepi yang membiaskan latar pada navbar & mini player. */
        const val DEFAULT_REFRACTION_HEIGHT_DP: Float = 14f
    }
}

/**
 * Konfigurasi palet warna & optik Liquid Glass sesuai Kyant0/AndroidLiquidGlass:
 * - Kapsul dasar: Translucent #121212 (40% alpha) di Dark Mode, #FAFAFA (40% alpha) di Light Mode.
 * - Indikator/Pill: 10%-15% alpha kontras dengan highlight & inner shadow.
 */
object ChromeGlass {
    const val DARK_BAR_COLOR: Long = 0x29000000L
    const val LIGHT_BAR_COLOR: Long = 0x33FFFFFFL

    const val DARK_PILL_COLOR: Long = 0x29000000L
    const val LIGHT_PILL_COLOR: Long = 0x38FFFFFFL

    const val SOLID_DARK_COLOR: Long = 0xFF1C1C1EL
    const val SOLID_LIGHT_COLOR: Long = 0xFFF2F2F7L

    /**
     * Konfigurasi efek kaca Kyant untuk bar bawah (navbar).
     */
    fun chromeBarGlass(
        isDark: Boolean,
        mode: GlassEffectMode = GlassEffectMode.PREMIUM
    ): LiquidGlassSettings {
        val base = LiquidGlassSettings(
            glassColorArgb = if (isDark) DARK_BAR_COLOR else LIGHT_BAR_COLOR,
            thickness = 24f,
            blurRadiusDp = 8f,
            chromaticAberration = if (mode == GlassEffectMode.PREMIUM) 0.01f else 0f,
            lightAngleRadians = LiquidGlassSettings.DEFAULT_LIGHT_ANGLE_RAD,
            lightIntensity = 0.50f,
            refractiveIndex = if (mode == GlassEffectMode.PREMIUM) 1.2f else 1.0f,
            saturation = 1.5f,
            drawSpecularRim = mode != GlassEffectMode.SOLID,
            // Padanan `LiquidBottomTabs` Kyant0: vibrancy() + blur(8dp) + lens, Highlight.Default (45°)
            // + Shadow.Default, tanpa inner shadow. Pita lensa 14dp (Kyant0: 24dp) agar tengah kapsul
            // tetap jernih dan distorsi hanya di tepi, seperti tab bar iOS.
            refractionHeight = LiquidGlassSettings.DEFAULT_REFRACTION_HEIGHT_DP,
            refractionAmount = 24f,
            depthEffect = false,
            innerShadowRadiusDp = 0f,
            innerShadowAlpha = 0f,
            // iOS 26 rim: two soft lobes along a vertical light axis, not a ring around
            // the whole shape (falloff 1 lit nearly every edge and read as an outline).
            highlightAlpha = 0.8f,
            highlightFalloff = 3f,
            highlightAngleDeg = 45f,
            useKyantHighlight = true,
            // On a dark page a shadow only weighs the glass down (ios27Dark: elevation 0).
            dropShadow = !isDark
        )
        return if (mode == GlassEffectMode.SOLID) solidGlass(base, isDark) else base
    }

    /**
     * Konfigurasi efek kaca Kyant untuk MiniPlayerPill (parameter optik sama dengan bar).
     */
    fun chromePillGlass(
        isDark: Boolean,
        mode: GlassEffectMode = GlassEffectMode.PREMIUM
    ): LiquidGlassSettings {
        val base = LiquidGlassSettings(
            glassColorArgb = if (isDark) DARK_PILL_COLOR else LIGHT_PILL_COLOR,
            thickness = 14f,
            blurRadiusDp = 8f,
            chromaticAberration = if (mode == GlassEffectMode.PREMIUM) 0.01f else 0f,
            lightAngleRadians = LiquidGlassSettings.DEFAULT_LIGHT_ANGLE_RAD,
            lightIntensity = 0.60f,
            refractiveIndex = if (mode == GlassEffectMode.PREMIUM) 1.2f else 1.0f,
            saturation = 1.5f,
            drawSpecularRim = mode != GlassEffectMode.SOLID,
            // Sama dengan kaca bar Kyant0: lens(14dp, 24dp), Highlight.Default (45°) + Shadow.Default
            refractionHeight = LiquidGlassSettings.DEFAULT_REFRACTION_HEIGHT_DP,
            refractionAmount = 24f,
            depthEffect = false,
            innerShadowRadiusDp = 0f,
            innerShadowAlpha = 0f,
            // iOS 26 rim: two soft lobes along a vertical light axis, not a ring around
            // the whole shape (falloff 1 lit nearly every edge and read as an outline).
            highlightAlpha = 0.8f,
            highlightFalloff = 3f,
            highlightAngleDeg = 45f,
            useKyantHighlight = true,
            // On a dark page a shadow only weighs the glass down (ios27Dark: elevation 0).
            dropShadow = !isDark
        )
        return if (mode == GlassEffectMode.SOLID) solidGlass(base, isDark) else base
    }

    /**
     * Mode padat (solid / opaque).
     */
    fun solidGlass(settings: LiquidGlassSettings, isDark: Boolean? = null): LiquidGlassSettings {
        val opaqueColor = when (isDark) {
            true -> SOLID_DARK_COLOR
            false -> SOLID_LIGHT_COLOR
            null -> (settings.glassColorArgb and 0x00FFFFFFL) or 0xFF000000L
        }
        return settings.copy(
            glassColorArgb = opaqueColor,
            blurRadiusDp = 0f,
            saturation = 1f,
            lightIntensity = 0f,
            ambientStrength = 0f,
            chromaticAberration = 0f,
            refractiveIndex = 1.0f,
            drawSpecularRim = false,
            refractionHeight = 0f,
            refractionAmount = 0f,
            innerShadowAlpha = 0f,
            highlightAlpha = 0f,
            dropShadow = false
        )
    }
}

/**
 * Komputasi specular rim fallback CPU untuk perangkat yang tidak mendukung AGSL shader highlight.
 */
object GlassRimMath {
    const val DEFAULT_LOBE_POWER: Int = 3
    const val DEFAULT_BUCKETS: Int = 12
    private const val KICK_FACTOR: Double = 0.45

    fun computeRimAlpha(
        normalX: Float,
        normalY: Float,
        isDark: Boolean,
        lightAngleRadians: Float = LiquidGlassSettings.DEFAULT_LIGHT_ANGLE_RAD,
        lobePower: Int = DEFAULT_LOBE_POWER
    ): Float {
        val lightX = cos(lightAngleRadians.toDouble())
        val lightY = -sin(lightAngleRadians.toDouble())
        val peak = if (isDark) 0.85 else 0.95

        val dot = normalX.toDouble() * lightX + normalY.toDouble() * lightY
        val key = max(dot, 0.0).pow(lobePower.toDouble())
        val kick = max(-dot, 0.0).pow(lobePower.toDouble()) * KICK_FACTOR
        val rawAlpha = (key + kick) * peak
        return rawAlpha.coerceIn(0.0, 1.0).toFloat()
    }

    fun quantizeBucket(
        alpha: Float,
        isDark: Boolean,
        buckets: Int = DEFAULT_BUCKETS
    ): Int {
        if (alpha < 0.03f || buckets <= 1) return 0
        val peak = if (isDark) 0.85f else 0.95f
        return ((alpha / peak) * (buckets - 1)).roundToInt().coerceIn(0, buckets - 1)
    }
}
