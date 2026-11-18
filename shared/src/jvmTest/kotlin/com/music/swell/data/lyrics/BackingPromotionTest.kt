package com.music.swell.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A long-held backing vocal taking the row from the lead is Apple Music's, rule
 * and motion, so these pin both: which lines qualify, and where everything in
 * the row is at each moment of the hand-over and back.
 */
class BackingPromotionTest {
    /** A lead sung 0–2 s and a backing sung from [backingStart] to [backingEnd]. */
    private fun line(
        backingStart: Long = 1_000,
        backingEnd: Long = 8_000,
        leadEnd: Long = 2_000,
        backingWords: List<LyricWord>? = null,
    ) = LyricLine(
        timeMs = 0,
        text = "lead line",
        words = listOf(LyricWord(0, 1_000, "lead"), LyricWord(1_000, leadEnd, "line")),
        background = LyricLine(
            timeMs = backingStart,
            text = "ooh ah",
            words = backingWords ?: listOf(
                LyricWord(backingStart, backingStart + 500, "ooh"),
                LyricWord(backingStart + 500, backingEnd, "ah"),
            ),
        ),
    )

    @Test
    fun aBackingHeldLongPastTheLeadTakesTheRow() {
        val promotion = assertNotNull(line().backingPromotion)
        assertEquals(2_000L, promotion.mainEndMs)
        assertEquals(1_000L, promotion.backingStartMs)
        assertEquals(8_000L, promotion.backingEndMs)
    }

    @Test
    fun sixSecondsOfBackingIsTheBarAndOnlyLongerQualifies() {
        // Exactly six seconds first word to last is not enough: Apple's test is
        // strictly longer.
        assertNull(line(backingStart = 1_000, backingEnd = 7_000).backingPromotion)
        assertNotNull(line(backingStart = 1_000, backingEnd = 7_001).backingPromotion)
    }

    @Test
    fun theBackingHasToOutlastTheLeadByTheGrowth() {
        // 549 ms past the lead is not time enough to grow into its place; 550 is.
        assertNull(line(backingStart = 0, backingEnd = 8_000, leadEnd = 7_451).backingPromotion)
        assertNotNull(line(backingStart = 0, backingEnd = 8_000, leadEnd = 7_450).backingPromotion)
    }

    @Test
    fun noBackingOrAnUntimedOneNeverQualifies() {
        assertNull(line().copy(background = null).backingPromotion)
        val untimed = line().let { it.copy(background = it.background!!.copy(words = emptyList())) }
        assertNull(untimed.backingPromotion)
        val leadUntimed = line().copy(words = emptyList())
        assertNull(leadUntimed.backingPromotion)
        // The backing's own line never has one of its own.
        assertNull(line().background!!.backingPromotion)
    }

    @Test
    fun blankWordsAreNotCountedAsSinging() {
        // A trailing blank word stretching the backing past six seconds is
        // silence, and does not make a short backing long.
        val padded = line(
            backingWords = listOf(
                LyricWord(1_000, 4_000, "ooh"),
                LyricWord(4_000, 9_000, " "),
            ),
        )
        assertNull(padded.backingPromotion)
    }

    private val promotion = BackingPromotion(mainEndMs = 2_000, backingStartMs = 1_000, backingEndMs = 8_000)

    private fun frameAt(
        ms: Long,
        reveal: Float = 1f,
        crossFade: Boolean = false,
        heldForMs: Long = 0,
    ) = PromotionFrame().also {
        promotion.frame(
            positionMs = ms,
            reveal = reveal,
            lead = 100f,
            small = 60f,
            big = 160f,
            room = 10f,
            smallScale = 0.5f,
            crossFade = crossFade,
            heldForMs = heldForMs,
            into = it,
        )
    }

    @Test
    fun beforeTheLeadEndsTheRowIsTheLeadWithTheBackingOpeningUnderIt() {
        val frame = frameAt(1_500, reveal = 0.5f)
        assertEquals(PromotionFrame.BEFORE, frame.phase)
        assertEquals(130f, frame.height, 1e-4f)
        assertEquals(1f, frame.leadAlpha)
        assertEquals(0f, frame.bigAlpha)
        assertEquals(100f, frame.smallTop)
    }

    @Test
    fun theGrowthPicksUpExactlyWhereTheOpenRowLeftOff() {
        val start = frameAt(2_000)
        assertEquals(PromotionFrame.PROMOTED, start.phase)
        assertEquals(160f, start.height, 1e-4f) // lead + the open small backing
        assertEquals(1f, start.leadAlpha, 1e-4f)
        assertEquals(0.5f, start.bigScale, 1e-4f)
        assertEquals(90f, start.bigTop, 1e-4f) // its text right under the lead
        assertEquals(1f, start.bigAlpha, 1e-4f)
        assertEquals(0f, start.smallAlpha, 1e-4f)
    }

    @Test
    fun halfwayThroughTheGrowthEverythingIsOnApplesContentCurve() {
        val frame = frameAt(2_275)
        val grow = contentCurve(0.5f)
        assertEquals(grow, frame.grow, 1e-5f)
        assertEquals(1f - grow, frame.leadAlpha, 1e-5f)
        assertEquals(-8f * grow, frame.leadRise, 1e-4f)
        assertEquals(4f * grow, frame.leadBlur, 1e-4f)
        assertEquals(0.5f + 0.5f * grow, frame.bigScale, 1e-5f)
    }

    @Test
    fun grownTheBackingHoldsTheLeadsPlaceAtFullSize() {
        val frame = frameAt(5_000)
        assertEquals(160f, frame.height, 1e-4f)
        assertEquals(0f, frame.leadAlpha, 1e-5f)
        assertEquals(0f, frame.bigTop, 1e-4f)
        assertEquals(1f, frame.bigScale, 1e-5f)
        assertEquals(1f, frame.bigAlpha, 1e-5f)
    }

    @Test
    fun aBackingThatStartsAfterTheLeadFadesInWithTheGrowth() {
        assertEquals(0f, frameAt(2_000, reveal = 0f).bigAlpha, 1e-5f)
        assertEquals(contentCurve(0.5f), frameAt(2_275, reveal = 0f).bigAlpha, 1e-5f)
    }

    @Test
    fun rowsThatWrapDifferentlyCrossFadeRatherThanMorph() {
        val start = frameAt(2_000, crossFade = true)
        assertEquals(0f, start.bigAlpha, 1e-5f)
        assertEquals(1f, start.smallAlpha, 1e-5f)
        assertEquals(1f, start.smallScale, 1e-5f)
        val grown = frameAt(2_550, crossFade = true)
        assertEquals(1f, grown.bigAlpha, 1e-5f)
        assertEquals(0f, grown.smallAlpha, 1e-5f)
    }

    @Test
    fun theExitHandsBackOnApplesExitCurve() {
        val start = frameAt(8_000)
        assertEquals(PromotionFrame.LEAVING, start.phase)
        assertEquals(160f, start.height, 1e-4f)
        assertEquals(0f, start.leadAlpha, 1e-5f)
        assertEquals(6f, start.leadRise, 1e-4f)
        assertEquals(1f, start.bigAlpha, 1e-5f)

        val mid = frameAt(8_400)
        val ease = cubicBezierCurve(0.5f, 0.22f, 1f, 0.36f, 1f)
        assertTrue(ease > 0.9f, "the exit curve front-loads the change")
        assertEquals(ease, mid.leadAlpha, 1e-5f)
        assertEquals(1f - ease, mid.bigAlpha, 1e-5f)
        assertEquals(1f - 0.05f * ease, mid.bigScale, 1e-5f)
        assertEquals(-6f * ease, mid.bigRise, 1e-4f)
        assertEquals(6f * ease, mid.bigBlur, 1e-4f)
        assertEquals(6f * (1f - ease), mid.leadBlur, 1e-4f)
        assertEquals(160f + (100f - 160f) * ease, mid.height, 1e-3f)

        val done = frameAt(8_800)
        assertEquals(PromotionFrame.AFTER, done.phase)
        assertEquals(100f, done.height)
        assertEquals(1f, done.leadAlpha)
        assertEquals(0f, done.bigAlpha)
    }

    @Test
    fun aHeldExitKeepsItsRoomAndDrawsAgainstTheBottomOfIt() {
        val mid = frameAt(8_400, heldForMs = 1_150)
        assertEquals(160f, mid.height)
        val visible = 160f + (100f - 160f) * cubicBezierCurve(0.5f, 0.22f, 1f, 0.36f, 1f)
        assertEquals(160f - visible, mid.shift, 1e-3f)

        val after = frameAt(9_000, heldForMs = 1_150)
        assertEquals(160f, after.height)
        assertEquals(60f, after.shift)

        val released = frameAt(9_150, heldForMs = 1_150)
        assertEquals(100f, released.height)
        assertEquals(0f, released.shift)
    }

    @Test
    fun theRowOnlyHearsTheClockWhileItIsMoving() {
        val rest = { ms: Long -> promotion.restingPoint(ms, revealFromMs = 750, revealMs = 400, heldForMs = 1_150) }
        // Before the backing opens, and once it has, nothing moves.
        assertEquals(rest(0), rest(700))
        assertEquals(800L, rest(800))
        assertEquals(rest(1_150), rest(1_999))
        // The growth moves; once grown the row holds still until the exit.
        assertEquals(2_100L, rest(2_100))
        assertEquals(rest(2_550), rest(7_999))
        assertEquals(8_300L, rest(8_300))
        // Held past the exit, and then given back: two still stretches.
        assertEquals(rest(8_800), rest(9_100))
        assertEquals(rest(9_150), rest(60_000))
        // Each stand-in draws the same frame as the moments it stands for.
        for (ms in longArrayOf(0, 1_500, 5_000, 9_000, 20_000)) {
            val real = frameAt(ms, heldForMs = 1_150)
            val stand = frameAt(rest(ms), heldForMs = 1_150)
            assertEquals(real.height, stand.height, 1e-4f, "at $ms")
            assertEquals(real.shift, stand.shift, 1e-4f, "at $ms")
            assertEquals(real.bigAlpha, stand.bigAlpha, 1e-4f, "at $ms")
            assertEquals(real.leadAlpha, stand.leadAlpha, 1e-4f, "at $ms")
        }
    }
}
