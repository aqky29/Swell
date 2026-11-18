package com.music.swell.ui.components.liquidglass.morph

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.min

/**
 * Just the rim of the resting pill, for a control that keeps its own fill.
 *
 * Over a page there is nothing for a lens to bend, so these controls draw a veil and the rim
 * alone. This is the same rim the pill's material draws, a key from above and a fill from
 * below, so a control with a veil and a control made of glass light up from the same side.
 *
 * [dark] says what the veil sits on, which is what the rim takes its colour from.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun Modifier.morphRim(shape: CornerBasedShape, dark: Boolean): Modifier {
  val shader = remember { RuntimeShader(MorphRimShader) }
  val brush = remember(shader) { ShaderBrush(shader) }
  return drawWithContent {
    drawContent()
    if (size.width <= 1f || size.height <= 1f) return@drawWithContent
    val radius = min(shape.topStart.toPx(size, this), min(size.width, size.height) / 2f)
    shader.setFloatUniform("center", size.width / 2f, size.height / 2f)
    shader.setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
    shader.setFloatUniform("cornerRadii", radius, radius)
    shader.setFloatUniform("density", density)
    shader.setFloatUniform("rimAlpha", if (dark) MorphRecipe.RimAlphaDark else MorphRecipe.RimAlphaLight)
    val base = if (dark) VeilOnDark else VeilOnLight
    shader.setFloatUniform("base", base, base, base)
    drawRect(brush)
  }
}

/** About what a 16% white veil comes to over a dark page, and over a light one. */
private const val VeilOnDark = .28f
private const val VeilOnLight = .92f
