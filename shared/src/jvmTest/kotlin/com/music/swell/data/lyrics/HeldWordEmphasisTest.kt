package com.music.swell.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The held-word emphasis is Apple Music's, number for number, so these pin the
 * numbers: who qualifies, how far a letter swells and when, and how the letters
 * make room for each other.
 */
class HeldWordEmphasisTest {
    private fun held(text: String, heldMs: Long, startMs: Long = 0): GrowingWord? =
        LyricLine(
            timeMs = startMs,
            text = text,
            words = listOf(LyricWord(startMs, startMs + heldMs, text)),
        ).growingWords.singleOrNull()

    /** The biggest letter [letter] of [word] gets, sampled every 5 ms. */
    private fun peakScale(word: GrowingWord, letter: Int): Float =
        (word.startMs..word.restsAtMs step 5).maxOf { 1f + word.emphasisAt(letter, it) * word.amount }

    @Test
    fun aWordHeldTwoSecondsSwellsByFourteenPercent() {
        // Two letters is short enough for each one to reach the top of its
        // envelope before it starts letting go.
        val word = assertNotNull(held("oh", 2_000))
        assertEquals(0.14f, word.amount, 1e-6f)
        assertEquals(1.14f, peakScale(word, 0), 1e-4f)
        assertEquals(1.14f, peakScale(word, 1), 1e-4f)
    }

    @Test
    fun aLongerWordLetsEachLetterGoBeforeItTops() {
        // Four letters over two seconds: each starts letting go 1 s into its
        // two-second ease, so it tops out at the curve's halfway value.
        val word = assertNotNull(held("hold", 2_000))
        val expected = 1f + 0.14f * contentCurve(0.5f)
        assertEquals(expected, peakScale(word, 0), 1e-4f)
        assertEquals(1.112f, expected, 1e-3f)
    }

    @Test
    fun aSecondIsTheBarAndItEarnsNoSwell() {
        assertNull(held("hold", 900))
        assertNull(held("hold", 999))
        val bare = assertNotNull(held("hold", 1_000))
        assertEquals(0f, bare.amount, 1e-6f)
        assertEquals(0.07f, assertNotNull(held("hold", 1_500)).amount, 1e-6f)
        // Capped at two seconds.
        assertEquals(0.14f, assertNotNull(held("hold", 5_000)).amount, 1e-6f)
    }

    @Test
    fun scriptsWhoseLettersDoNotStandAloneAreLeftAlone() {
        assertNull(held("一二三", 2_000))
        assertNull(held("こころ", 2_000))
        assertNull(held("사랑", 2_000))
        assertNull(held("שלום", 2_000))
        assertNull(held("नमस्ते", 2_000))
        assertNotNull(held("café", 2_000))
    }

    @Test
    fun lettersAreGraphemesAndSevenIsTheMost() {
        assertEquals(7, assertNotNull(held("holding", 2_000)).letters)
        assertNull(held("standing", 2_000))
        // A decomposed accent and an emoji are one letter each.
        val accent = assertNotNull(held("café", 2_000))
        assertEquals(4, accent.letters)
        assertEquals(3, accent.letterStart(3))
        assertEquals(5, accent.letterEnd(3))
        assertEquals(2, assertNotNull(held("a😀", 2_000)).letters)
    }

    @Test
    fun eachLetterWaitsItsTurn() {
        // 0.4 · 2000 / 4 = 200 ms apart.
        val word = assertNotNull(held("hold", 2_000, startMs = 10_000))
        for (letter in 0 until word.letters) {
            val opens = 10_000L + letter * 200L
            assertEquals(0f, word.emphasisAt(letter, opens - 1))
            assertTrue(word.emphasisAt(letter, opens + 20) > 0f)
        }
        // Capped at 400 ms however long the hold.
        val long = assertNotNull(held("oh", 10_000))
        assertEquals(0f, long.emphasisAt(1, 399))
        assertTrue(long.emphasisAt(1, 420) > 0f)
    }

    @Test
    fun theWordIsAtRestFromRestsAtOn() {
        val word = assertNotNull(held("golden", 2_400, startMs = 5_000))
        val last = word.letters - 1
        assertTrue(word.emphasisAt(last, word.restsAtMs - 200) > 0f)
        for (at in listOf(word.restsAtMs, word.restsAtMs + 1, word.restsAtMs + 10_000)) {
            for (letter in 0 until word.letters) assertEquals(0f, word.emphasisAt(letter, at))
        }
        // 5000 + 5 · min(0.4 · 2400 / 6, 400) + 2400 · 2 / 6 + 2400.
        assertEquals(5_000L + 800 + 800 + 2_400, word.restsAtMs)
    }

    @Test
    fun theSpreadIsSymmetricAndLeavesTheMiddleAlone() {
        val tx = FloatArray(4)
        spreadLetters(floatArrayOf(2f, 2f, 2f, 2f), 4, rtl = false, into = tx)
        assertEquals(-tx[3], tx[0], 1e-6f)
        assertEquals(-tx[2], tx[1], 1e-6f)
        assertTrue(tx[0] < tx[1] && tx[1] < 0f)

        val odd = FloatArray(3)
        spreadLetters(floatArrayOf(2f, 2f, 2f), 3, rtl = false, into = odd)
        assertEquals(0f, odd[1])
        assertEquals(-odd[2], odd[0], 1e-6f)

        val rtl = FloatArray(4)
        spreadLetters(floatArrayOf(2f, 2f, 2f, 2f), 4, rtl = true, into = rtl)
        for (i in 0 until 4) assertEquals(-tx[i], rtl[i], 1e-6f)

        val one = floatArrayOf(5f)
        spreadLetters(floatArrayOf(3f), 1, rtl = false, into = one)
        assertEquals(0f, one[0])
    }

    @Test
    fun sampledLettersCarryTheSwellTheGlowAndTheSpread() {
        val word = assertNotNull(held("hold", 2_000))
        val swell = WordSwell()
        for (letter in 0 until word.letters) swell.width[letter] = 20f
        word.sampleInto(900, rtl = false, into = swell)
        for (letter in 0 until word.letters) {
            val through = word.emphasisAt(letter, 900)
            assertEquals(1f + through * 0.14f, swell.scale[letter], 1e-6f)
            assertEquals(through, swell.glow[letter], 1e-6f)
        }
        assertTrue(swell.shift[0] < 0f && swell.shift[3] > 0f)
        // A word held exactly a second neither swells nor lights.
        val bare = assertNotNull(held("hold", 1_000))
        bare.sampleInto(500, rtl = false, into = swell)
        for (letter in 0 until bare.letters) {
            assertEquals(1f, swell.scale[letter], 1e-6f)
            assertEquals(0f, swell.glow[letter], 1e-6f)
            assertEquals(0f, swell.shift[letter], 1e-6f)
        }
    }

    @Test
    fun theContentCurveIsCssEase() {
        assertEquals(0f, contentCurve(0f))
        assertEquals(1f, contentCurve(1f))
        // cubic-bezier(0.25, 0.1, 0.25, 1) at its halfway mark.
        assertEquals(0.8024f, contentCurve(0.5f), 1e-3f)
        var previous = 0f
        for (step in 1..100) {
            val y = contentCurve(step / 100f)
            assertTrue(y >= previous)
            previous = y
        }
    }
}
