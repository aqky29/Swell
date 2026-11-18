package com.music.swell.ui.components.liquidglass.morph

import kotlin.math.max

/**
 * The reference's default morph timing: how fast the trigger collapses, how springy the menu
 * is ejected, and how softly it is absorbed on close. [absorbDuration] is the one override the
 * reference's own top-right toolbar uses, to close a little slower than the default.
 */
internal class MorphTuning(absorbDuration: Float = DefaultAbsorbDuration) {
  private val shrinkDuration = max(DefaultShrinkDuration, MinimumDuration)

  val shrinkScale = DefaultShrinkScale
  val collapse = MorphSpringSpec(1f, shrinkDuration)
  val eject = MorphSpringSpec(DefaultEjectDamping, shrinkDuration * EjectRatio)
  val absorb = MorphSpringSpec(DefaultAbsorbDamping, max(absorbDuration, MinimumDuration))
  val neckHold = max(shrinkDuration / DefaultShrinkDuration, 1f)

  companion object {
    /** The close duration the reference's top-right toolbar passes. */
    const val ToolbarAbsorbDuration = .48f

    private const val DefaultShrinkDuration = .21f
    private const val DefaultShrinkScale = .25f
    private const val DefaultEjectDamping = .75f
    private const val DefaultAbsorbDuration = .35f
    private const val DefaultAbsorbDamping = .8f
    private const val EjectRatio = .32f / .21f
    private const val MinimumDuration = .02f
  }
}
