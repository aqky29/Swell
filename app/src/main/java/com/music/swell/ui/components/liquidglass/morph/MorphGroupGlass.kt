package com.music.swell.ui.components.liquidglass.morph

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.min

/**
 * What a bar's glass looks like, apart from where its shapes are: the body colour the user chose
 * in Settings → Liquid Glass, and how far two capsules reach for each other.
 */
internal class MorphGroupLook(
  val density: Float,
  val dark: Boolean,
  val mergeDistance: Float,
  /** ARGB of the glass body laid over the backdrop. */
  val bodyArgb: Int,
)

/**
 * The resting pill's material over up to four capsules at once, for the navigation bar.
 *
 * [shapes] holds a centre and half size for each capsule as four floats, [alphas] how present
 * each one is. The effect is meant for a layer whose content is the bar's backdrop, already
 * blurred, and whose coordinates the shapes are in.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MorphGroupGlass {
  private val shader = RuntimeShader(MorphGroupShader)

  fun effect(
    shapes: FloatArray,
    alphas: FloatArray,
    width: Float,
    height: Float,
    look: MorphGroupLook,
  ): RenderEffect {
    val density = look.density
    val control = if (look.dark) MorphRecipe.ControlDark else MorphRecipe.ControlLight

    for (i in 0 until ShapeCount) {
      shader.setFloatUniform("shape$i", shapes[i * 4], shapes[i * 4 + 1], shapes[i * 4 + 2], shapes[i * 4 + 3])
    }
    shader.setFloatUniform("shapeAlpha", alphas[0], alphas[1], alphas[2], alphas[3])
    shader.setFloatUniform("mergeDistance", look.mergeDistance)

    shader.setFloatUniform("nodeOrigin", 0f, 0f)
    shader.setFloatUniform("sampleExtent", width, height)
    shader.setFloatUniform("density", density)
    shader.setFloatUniform("face", control.saturation, control.black, control.white, control.rollOff)
    shader.setFloatUniform("faceFill", 1f, 1f, 1f, control.fillAlpha)
    val body = look.bodyArgb
    shader.setFloatUniform(
      "tint",
      ((body shr 16) and 0xFF) / 255f,
      ((body shr 8) and 0xFF) / 255f,
      (body and 0xFF) / 255f,
      ((body ushr 24) and 0xFF) / 255f,
    )

    // The lens follows the size of the glass, as the morph's does; a bar's capsules differ in
    // height, so it follows the smallest one that is really there.
    var smallest = Float.MAX_VALUE
    for (i in 0 until ShapeCount) {
      if (alphas[i] > Present && shapes[i * 4 + 2] > 0f && shapes[i * 4 + 3] > 0f) {
        smallest = min(smallest, 2f * min(shapes[i * 4 + 2], shapes[i * 4 + 3]))
      }
    }
    val extent = (if (smallest == Float.MAX_VALUE) 0f else smallest) / density
    shader.setFloatUniform("lensHeight", min(extent * .25f, 20f) * density)
    shader.setFloatUniform("lensAmount", -min(extent * .5f, 60f) * density)
    shader.setFloatUniform("outerHeight", max(16f, extent * .2f) * density)
    shader.setFloatUniform("outerAmount", -max(16f, extent * .25f) * density)
    shader.setFloatUniform("keyThreshold", if (look.dark) .2588f else -.2756f)
    shader.setFloatUniform("highlightAlpha", 1f)
    shader.setFloatUniform("rimAlpha", if (look.dark) MorphRecipe.RimAlphaDark else MorphRecipe.RimAlphaLight)
    shader.setFloatUniform("surfaceOpacity", 1f)
    shader.setFloatUniform("press", 0f, 0f, 1f, 0f)
    shader.setFloatUniform("pressLift", 0f)
    shader.setFloatUniform("rimLift", 0f)

    return RenderEffect.createRuntimeShaderEffect(shader, "content")
  }

  companion object {
    const val ShapeCount = 4

    /** Below this a shape is on its way out and does not set the lens. */
    private const val Present = .5f
  }
}
