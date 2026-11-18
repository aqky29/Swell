package com.music.swell.ui.components.liquidglass.morph

internal class MorphRecipe(
  val saturation: Float,
  val black: Float,
  val white: Float,
  val rollOff: Float,
  val fillAlpha: Float,
  val tint: Float = 0f,
  val tintAlpha: Float = 0f,
) {
  companion object {
    val ControlDark = MorphRecipe(1.3f, .125f, 1.125f, .4f, 0f)
    val MenuDark = MorphRecipe(1.3f, .125f, 1.125f, .65f, 0f, tint = .042f, tintAlpha = .7f)
    val ControlLight = MorphRecipe(1.2f, .4f, 1.03f, 0f, .2f)
    val MenuLight = MorphRecipe(1.2f, .4f, 1.03f, 0f, .2f, tint = .975f, tintAlpha = .5f)

    /** How strong the rim's soft stroke is on a dark and on a light backdrop. */
    const val RimAlphaDark = 1f
    const val RimAlphaLight = .55f
  }
}
