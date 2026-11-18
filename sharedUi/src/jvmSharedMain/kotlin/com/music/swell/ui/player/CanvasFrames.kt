package com.music.swell.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * When a playing Canvas clip last handed a frame to its surface, so the lyric
 * clock can tick on the same frame instead of beside it.
 *
 * A clip drawn through a view the app composites redraws the window every time
 * a frame lands. A lyric clock ticking on its own timer is out of phase with
 * that, so the window ended up drawing the union of the two: a 30 Hz strip
 * over a 30 fps clip measured close to 47 frames a second, and the 60 Hz
 * panel close to 77. Riding the clip's frames, the lyric's redraw is folded
 * into one the window was going to make anyway.
 *
 * Fed by the platform's decoder from whatever thread it renders on, just
 * before a frame is released to the surface; nothing feeds it where the clip
 * is composited by the system instead.
 */
object CanvasFrames {
    private val released = MutableStateFlow(0L)

    @Volatile
    private var spacing = 0L

    /** The last release, on the [System.nanoTime] clock; 0 before any. */
    internal val lastReleased: StateFlow<Long> get() = released

    /**
     * The clip's frame spacing, smoothed: the decoder releases on a loop of its
     * own, so any one gap is off by up to a tick either way. 0 until known.
     */
    internal val intervalNanos: Long get() = spacing

    /** A frame is about to reach the surface. Any thread. */
    fun onFrameReleased() {
        val now = System.nanoTime()
        val gap = now - released.value
        if (gap in MIN_SPACING_NANOS..MAX_SPACING_NANOS) {
            val smoothed = spacing
            spacing = if (smoothed == 0L) gap else smoothed + (gap - smoothed) / 8
        }
        released.value = now
    }

    internal const val MIN_SPACING_NANOS = 8_000_000L
    internal const val MAX_SPACING_NANOS = 100_000_000L
}
