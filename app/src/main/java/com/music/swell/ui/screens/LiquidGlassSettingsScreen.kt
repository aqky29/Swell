package com.music.swell.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.swell.R
import com.music.swell.data.settings.AppSettings
import com.music.swell.ui.components.liquidglass.LiquidGlassBox
import com.music.swell.ui.components.liquidglass.currentSwellGlass
import com.music.swell.ui.components.liquidglass.liquidGlassBackdropSource
import com.music.swell.ui.components.liquidglass.rememberLiquidGlassBackdropState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Settings → Liquid Glass, laid out after Music OS 26's page of the same name
 * (`Screens/Settings/LiquidBlurSettings`): a live preview with content drifting
 * under the glass, the iOS 26 Clear/Tinted choice with its opacity, a switch
 * for the blur, and the lens tuning below. Every change lands in the preview
 * and across the app at once, since both read [currentSwellGlass].
 */
@Composable
fun LiquidGlassSettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val glassSolid by AppSettings.glassSolid.collectAsStateWithLifecycle()
    val tinted by AppSettings.glassTinted.collectAsStateWithLifecycle()
    val opacity by AppSettings.glassOpacity.collectAsStateWithLifecycle()
    val blurEnabled by AppSettings.glassBlurEnabled.collectAsStateWithLifecycle()
    val glassThickness by AppSettings.glassThickness.collectAsStateWithLifecycle()
    val glassIor by AppSettings.glassRefractiveIndex.collectAsStateWithLifecycle()
    val glassBlur by AppSettings.glassBlur.collectAsStateWithLifecycle()
    val lightAngle by AppSettings.glassLightAngle.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        GlassPreview(
            modifier = Modifier
                .padding(horizontal = GROUP_INSET)
                .padding(top = 8.dp),
        )

        SettingsGroup(
            footer = stringResource(R.string.liquid_glass_mode_subtitle),
        ) {
            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.liquid_glass),
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.glass_mode_premium),
                    stringResource(R.string.glass_mode_solid),
                ),
                selectedIndex = if (glassSolid) 1 else 0,
                onSelect = { AppSettings.setGlassSolid(it == 1) },
                modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 14.dp),
            )
        }

        if (glassSolid) return@Column

        SettingsGroup(
            header = stringResource(R.string.glass_appearance),
            footer = stringResource(R.string.glass_style_footer),
        ) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.glass_style_clear),
                    stringResource(R.string.glass_style_tinted),
                ),
                selectedIndex = if (tinted) 1 else 0,
                onSelect = { AppSettings.setGlassTinted(it == 1) },
                modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, top = 14.dp, bottom = 6.dp),
            )
            SliderRow(
                icon = Icons.Rounded.Contrast,
                title = stringResource(R.string.glass_opacity),
                value = "${(opacity * 100).roundToInt()}%",
                sliderValue = opacity,
                onSliderValue = AppSettings::setGlassOpacity,
                valueRange = 0f..1f,
                steps = 0,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.BlurOn,
                title = stringResource(R.string.glass_blur_effects),
                subtitle = stringResource(R.string.glass_blur_effects_subtitle),
                trailing = {
                    Switch(
                        checked = blurEnabled,
                        onCheckedChange = AppSettings::setGlassBlurEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setGlassBlurEnabled(!blurEnabled) },
            )
        }

        SettingsGroup(header = stringResource(R.string.glass_advanced)) {
            // One slider for the three below: all of them move along their ranges together.
            val intensity = ((glassIor - AppSettings.MIN_GLASS_IOR) /
                (AppSettings.MAX_GLASS_IOR - AppSettings.MIN_GLASS_IOR)).coerceIn(0f, 1f)
            SliderRow(
                icon = Icons.Rounded.Opacity,
                title = stringResource(R.string.glass_intensity),
                value = "${(intensity * 100).roundToInt()}%",
                sliderValue = intensity,
                onSliderValue = { t ->
                    AppSettings.setGlassThickness(lerp(AppSettings.MIN_GLASS_THICKNESS, AppSettings.MAX_GLASS_THICKNESS, t))
                    AppSettings.setGlassRefractiveIndex(lerp(AppSettings.MIN_GLASS_IOR, AppSettings.MAX_GLASS_IOR, t))
                    AppSettings.setGlassBlur(lerp(AppSettings.MIN_GLASS_BLUR, AppSettings.MAX_GLASS_BLUR, t))
                },
                valueRange = 0f..1f,
                steps = 0,
            )
            SliderRow(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.glass_thickness),
                value = "${glassThickness.roundToInt()} dp",
                sliderValue = glassThickness,
                onSliderValue = AppSettings::setGlassThickness,
                valueRange = AppSettings.MIN_GLASS_THICKNESS..AppSettings.MAX_GLASS_THICKNESS,
                steps = 0,
            )
            SliderRow(
                icon = Icons.Rounded.WaterDrop,
                title = stringResource(R.string.glass_refraction),
                value = "%.2f".format(glassIor),
                sliderValue = glassIor,
                onSliderValue = AppSettings::setGlassRefractiveIndex,
                valueRange = AppSettings.MIN_GLASS_IOR..AppSettings.MAX_GLASS_IOR,
                steps = 0,
            )
            SliderRow(
                icon = Icons.Rounded.LightMode,
                title = stringResource(R.string.glass_light_angle),
                value = "${lightAngle.roundToInt()}°",
                sliderValue = lightAngle,
                onSliderValue = AppSettings::setGlassLightAngle,
                valueRange = AppSettings.MIN_GLASS_LIGHT_ANGLE..AppSettings.MAX_GLASS_LIGHT_ANGLE,
                steps = 0,
            )
            if (blurEnabled) {
                SliderRow(
                    icon = Icons.Rounded.BlurOn,
                    title = stringResource(R.string.glass_blur),
                    value = "${glassBlur.roundToInt()} dp",
                    sliderValue = glassBlur,
                    onSliderValue = AppSettings::setGlassBlur,
                    valueRange = AppSettings.MIN_GLASS_BLUR..AppSettings.MAX_GLASS_BLUR,
                    steps = 0,
                )
            }
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.glass_reset),
                onClick = AppSettings::resetGlassTuning,
            )
        }
    }
}

/** Colour blobs for the preview, after the gradient cards Music OS 26 scrolls under its glass. */
private val PreviewBlobs = listOf(
    Color(0xFF22C55E), // cosmic green
    Color(0xFFA855F7), // neon purple
    Color(0xFFF97316), // volcanic orange
    Color(0xFFE11D48), // magma red
    Color(0xFF38BDF8), // mercury blue
)

/**
 * Content drifting under a glass tab bar and a round button, both drawn with
 * the app's own [currentSwellGlass] so the preview is exactly what the bars
 * will look like. The backdrop is this card's own, not the page's.
 */
@Composable
private fun GlassPreview(modifier: Modifier = Modifier) {
    val glass = currentSwellGlass()
    val backdrop = rememberLiquidGlassBackdropState()
    val drift by rememberInfiniteTransition(label = "glassPreview").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    // The glass re-reads the backdrop on this tick; the blobs move every frame.
    LaunchedEffect(backdrop) {
        snapshotFlow { drift }.collect { backdrop.notifyContentChanged() }
    }
    val baseColor = if (glass.isDark) Color(0xFF0B0B10) else Color(0xFFF4F1EC)
    val onContent = if (glass.isDark) Color.White else Color(0xFF1C1C1E)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(GroupShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(backdrop, baseColor)
                .drawBehind {
                    PreviewBlobs.forEachIndexed { i, color ->
                        val phase = drift + i * 1.3f
                        val center = Offset(
                            x = size.width * (0.5f + 0.38f * cos(phase * (if (i % 2 == 0) 1f else -1f))),
                            y = size.height * (0.45f + 0.32f * sin(phase + i)),
                        )
                        val radius = size.minDimension * (0.42f + 0.06f * (i % 3))
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color, color.copy(alpha = 0f)),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                    }
                },
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.glass_preview).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContent.copy(alpha = 0.7f),
                )
                Text(
                    text = "Liquid Mercury",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onContent,
                )
                Text(
                    text = "Minimalist Frost · Cosmic Aurora",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContent.copy(alpha = 0.8f),
                )
            }
        }

        val iconTint = if (glass.isDark) Color.White else Color(0xFF1C1C1E)
        LiquidGlassBox(
            backdropState = backdrop,
            settings = glass.pill,
            mode = glass.mode,
            isDark = glass.isDark,
            cornerRadius = 22.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        LiquidGlassBox(
            backdropState = backdrop,
            settings = glass.bar,
            mode = glass.mode,
            isDark = glass.isDark,
            cornerRadius = 30.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .width(236.dp)
                .height(60.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(Icons.Rounded.Home, Icons.Rounded.LibraryMusic, Icons.Rounded.Search).forEachIndexed { i, icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (i == 0) Color(0xFFE8163A) else iconTint,
                    )
                }
            }
        }
    }
}
