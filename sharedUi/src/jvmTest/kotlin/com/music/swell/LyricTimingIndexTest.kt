package com.music.swell

import com.music.swell.data.lyrics.LyricLine
import com.music.swell.ui.player.LyricTimingIndex
import com.music.swell.ui.player.activeLyricRows
import com.music.swell.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTimingIndexTest {
    @Test
    fun `lookup preserves duplicate timestamps and backward seeks`() {
        val lines = listOf(
            LyricLine(100, "first"),
            LyricLine(100, "second"),
            LyricLine(200, "third"),
        )
        val timing = LyricTimingIndex(lines)
        for (position in listOf(0L, 100L, 199L, 200L, 900L, 99L, 100L)) {
            assertEquals(lines.indexOfLast { it.timeMs <= position }, timing.indexAt(position))
        }
        assertEquals(-1, LyricTimingIndex(emptyList()).indexAt(100))
    }

    @Test
    fun `indexed rows preserve long backing vocals gaps and exact end boundaries`() {
        val lines = listOf(
            LyricLine(100, "lead", sungUntilMs = 200,
                background = LyricLine(150, "backing", sungUntilMs = 1_000)),
            LyricLine(250, "next", sungUntilMs = 400),
            LyricLine(450, ""),
            LyricLine(600, "last", sungUntilMs = 800),
        )
        val timing = LyricTimingIndex(lines)
        for (position in 0L..1_200L) {
            assertEquals("position=$position", activeLyricRows(lines, position), timing.activeRowsAt(position))
        }
    }

    @Test
    fun `unsorted input retains existing selection behavior`() {
        val lines = listOf(LyricLine(200, "later"), LyricLine(100, "earlier"))
        val timing = LyricTimingIndex(lines)
        for (position in 0L..300L) {
            assertEquals(lines.indexOfLast { it.timeMs <= position }, timing.indexAt(position))
            assertEquals(activeLyricRows(lines, position), timing.activeRowsAt(position))
        }
    }

    @Test
    fun `smart idle scheduling sleeps during static windows on line-synced lyrics`() {
        val lines = listOf(
            LyricLine(10_000, "First line"),
            LyricLine(25_000, "Second line"),
        )
        val timing = LyricTimingIndex(lines)
        assertFalse(timing.hasWordSync)

        // At 0ms (far before first line): idle and sleeps
        assertFalse(timing.isAnimatingAt(0L))
        assertTrue(timing.nextDeadlineDeltaMs(0L) > 0L)
        assertEquals(250L, timing.nextDeadlineDeltaMs(0L, maxIdleMs = 250L))

        // Approaching first line (within scrollLead, e.g. at 9_500ms): active
        assertTrue(timing.isAnimatingAt(9_500L))
        assertEquals(0L, timing.nextDeadlineDeltaMs(9_500L))

        // Settled on first line at 15_000ms: idle until scrollLead into second line
        assertFalse(timing.isAnimatingAt(15_000L))
        assertTrue(timing.nextDeadlineDeltaMs(15_000L) > 0L)

        // Approaching second line at 24_500ms: active
        assertTrue(timing.isAnimatingAt(24_500L))
        assertEquals(0L, timing.nextDeadlineDeltaMs(24_500L))
    }

    @Test
    fun `smart idle scheduling activates on word sync and sleeps during instrumental breaks`() {
        val lines = listOf(
            LyricLine(
                timeMs = 5_000,
                text = "word one",
                words = listOf(LyricWord(5_000, 7_000, "word"), LyricWord(7_000, 8_000, "one")),
            ),
            LyricLine(
                timeMs = 20_000,
                text = "word two",
                words = listOf(LyricWord(20_000, 22_000, "word"), LyricWord(22_000, 23_000, "two")),
            ),
        )
        val timing = LyricTimingIndex(lines)
        assertTrue(timing.hasWordSync)

        // During vocal animation [5_000..8_000 + RISE_MS]: active
        assertTrue(timing.isAnimatingAt(6_000L))
        assertEquals(0L, timing.nextDeadlineDeltaMs(6_000L))

        // In instrumental break at 12_000ms: idle
        assertFalse(timing.isAnimatingAt(12_000L))
        assertTrue(timing.nextDeadlineDeltaMs(12_000L) > 0L)

        // Approaching line 2 at 19_500ms: active
        assertTrue(timing.isAnimatingAt(19_500L))
        assertEquals(0L, timing.nextDeadlineDeltaMs(19_500L))
    }

    @Test
    fun `smart idle scheduling activates during gap dot progress`() {
        val lines = listOf(
            LyricLine(5_000, "Intro"),
            LyricLine(10_000, ""), // Gap from 10_000 to 18_000
            LyricLine(18_000, "Verse"),
        )
        val timing = LyricTimingIndex(lines)
        assertTrue(timing.hasGaps)

        // During gap at 12_000ms: active
        assertTrue(timing.isAnimatingAt(12_000L))
        assertEquals(0L, timing.nextDeadlineDeltaMs(12_000L))
    }
}
