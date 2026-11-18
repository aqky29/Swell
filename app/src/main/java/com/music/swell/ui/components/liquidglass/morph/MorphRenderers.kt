package com.music.swell.ui.components.liquidglass.morph

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Shared renderers. Compiling a shader is the slow part, so a menu and the resting controls
 * each compile theirs once. Effects copy a shader's uniforms when they are made, so one shader
 * can serve every control on the main thread.
 *
 * The menu's renderer keeps a neck texture while two shapes are fused; the controls' renderer
 * never does, so the two are kept apart and a button drawn mid-morph cannot disturb the menu.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object MorphRenderers {
  private var shared: MorphRenderer? = null
  private var sharedDensity = 0f
  private var controls: MorphRenderer? = null
  private var controlsDensity = 0f
  private var bar: MorphGroupGlass? = null

  /** The renderer for the menu that is morphing out of a control. */
  fun obtain(density: Float): MorphRenderer {
    val current = shared
    if (current != null && sharedDensity == density) return current
    return MorphRenderer(density).also {
      shared = it
      sharedDensity = density
    }
  }

  /** The renderer for controls at rest: buttons, pills and any single-shape glass. */
  fun resting(density: Float): MorphRenderer {
    val current = controls
    if (current != null && controlsDensity == density) return current
    return MorphRenderer(density).also {
      controls = it
      controlsDensity = density
    }
  }

  /** The renderer for a navigation bar's capsules. */
  fun bar(): MorphGroupGlass = bar ?: MorphGroupGlass().also { bar = it }
}
