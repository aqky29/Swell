package com.music.swell

import com.music.swell.data.lyrics.LyricLine
import com.music.swell.data.lyrics.LyricWord
import com.music.swell.ui.player.activeLyricRows
import com.music.swell.ui.player.promotedHoldRow
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricFocusTest {
    private val overlap = listOf(
        LyricLine(1_000, "upper", sungUntilMs = 4_000),
        LyricLine(2_000, "middle", sungUntilMs = 5_000),
        LyricLine(3_000, "lower", sungUntilMs = 6_000),
    )

    @Test fun upperLineKeepsAnchorAcrossThreeOverlappingVocals() {
        assertEquals(listOf(0, 1, 2), activeLyricRows(overlap, 3_500))
    }

    @Test fun anchorAdvancesExactlyWhenUpperVocalEnds() {
        assertEquals(listOf(1, 2), activeLyricRows(overlap, 4_000))
        assertEquals(listOf(2), activeLyricRows(overlap, 5_000))
    }

    @Test fun finishedMiddleRowDoesNotCutOffLongUpperVocal() {
        val lines = overlap.toMutableList()
        lines[1] = lines[1].copy(sungUntilMs = 2_900)
        assertEquals(listOf(0, 2), activeLyricRows(lines, 3_500))
    }

    @Test fun backgroundVocalKeepsItsParentRowAnchored() {
        val lines = listOf(
            LyricLine(1_000, "lead", sungUntilMs = 1_900,
                background = LyricLine(1_500, "echo", sungUntilMs = 3_000)),
            LyricLine(2_000, "next", sungUntilMs = 4_000),
        )
        assertEquals(listOf(0, 1), activeLyricRows(lines, 2_500))
        assertEquals(listOf(1), activeLyricRows(lines, 3_000))
    }

    @Test fun plainLrcAdvancesWithoutInventingAnOverlap() {
        val lines = overlap.map { it.copy(sungUntilMs = null) }
        assertEquals(listOf(1), activeLyricRows(lines, 2_500))
    }

    @Test fun backwardSeekRecomputesTheAnchor() {
        assertEquals(listOf(2), activeLyricRows(overlap, 5_500))
        assertEquals(listOf(0), activeLyricRows(overlap, 1_500))
        assertEquals(emptyList<Int>(), activeLyricRows(overlap, 500))
    }

    /** A lead sung 1–2 s whose backing is held 1.5–9 s: long enough to take the row. */
    private val promoted = LyricLine(
        1_000, "lead",
        words = listOf(LyricWord(1_000, 2_000, "lead")),
        background = LyricLine(
            1_500, "ooh",
            words = listOf(LyricWord(1_500, 9_000, "ooh")),
        ),
    )

    @Test fun aPromotedRowHoldsTheFocusFromTheLeadsEndToTheBackingsEnd() {
        val lines = listOf(promoted, LyricLine(3_000, "next", sungUntilMs = 5_000))
        val promotions = lines.map { it.backingPromotion }
        val rows = intArrayOf(0)
        assertEquals(-1, promotedHoldRow(promotions, rows, 1_999))
        assertEquals(0, promotedHoldRow(promotions, rows, 2_000))
        // Newer lines singing beneath it do not take the focus away.
        assertEquals(0, promotedHoldRow(promotions, rows, 4_000))
        assertEquals(0, promotedHoldRow(promotions, rows, 8_999))
        // The exit runs with the panel already moving on.
        assertEquals(-1, promotedHoldRow(promotions, rows, 9_000))
    }

    @Test fun anUnpromotedSongNeverHolds() {
        val promotions = overlap.map { it.backingPromotion }
        assertEquals(-1, promotedHoldRow(promotions, IntArray(0), 3_500))
    }
}
