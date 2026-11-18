package com.music.swell.ui.components.liquidglass.morph

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * What a finger does to a resting pill: it swells under the touch, leans after a drag, and a
 * soft light follows the finger. The reference's `GlassJelly`, plus the frame loop it needs.
 *
 * [tick] is raised after every step, so the layers that draw from it redraw and nothing
 * recomposes.
 */
internal class MorphPress {
  val jelly = MorphJelly()

  var tick by mutableLongStateOf(0L)
    private set

  fun down(x: Float, y: Float) {
    jelly.down(x, y)
    tick++
  }

  fun move(x: Float, y: Float) {
    jelly.move(x, y)
    tick++
  }

  fun release() {
    jelly.release()
    tick++
  }

  /** Steps the springs on the frame clock until they have all come to rest. */
  suspend fun settle() {
    var last = withFrameNanos { it }
    while (!jelly.isSettled) {
      val now = withFrameNanos { it }
      val seconds = ((now - last) / 1_000_000_000f).coerceIn(0f, MaxStepSeconds)
      last = now
      jelly.advance(seconds)
      tick++
    }
    tick++
  }

  /** The pill's rect under the touch: [out] is the resting rect, from the origin, and is deformed. */
  fun deform(out: RectF, density: Float) {
    jelly.deformControl(out, density, 1f)
  }

  /** Writes the finger's light into [state]; [originX] and [originY] are the resting rect's corner. */
  fun light(state: MorphSurfaceState, originX: Float, originY: Float, density: Float) {
    val glow = jelly.glowValue
    state.pressX = originX + jelly.lightPositionX
    state.pressY = originY + jelly.lightPositionY
    state.pressRadius = LightRadius * density
    state.poolGain = LightPool * glow
    state.pressLift = LightLift * glow
    state.rimLift = LightRimLift * glow
  }

  internal companion object {
    private const val MaxStepSeconds = .05f

    /** The finger's light, in dp, and how much it lifts the whole face and pools under the finger. */
    const val LightRadius = 38f
    const val LightLift = .055f
    const val LightPool = .07f
    const val LightRimLift = .3f
  }
}
