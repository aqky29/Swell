package com.music.swell.data.lyrics

import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One word of a line, with the stretch of the song it is sung over.
 *
 * Apple's TTML splits long words into syllables. The word is still the unit
 * the line is laid out, lifted and lit in — "e" and "nough" are one word on
 * screen — so [startMs] is the first syllable's start and [endMs] the last
 * one's end. But the syllables keep their own timing in [syllables], and the
 * sweep follows those: "e" is clipped and "nough" is held, and a wipe spread
 * evenly over the pair runs ahead of the first half and behind the second.
 *
 * Empty for a word nobody split, which is most of them — the word is then its
 * own single syllable.
 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList(),
) {
    /**
     * How many of [text]'s characters have been sung at [positionMs], as a
     * fraction. Each syllable fills its own characters over its own time, and
     * a pause between two of them rests on the boundary rather than creeping
     * into the next one before it is sung.
     */
    fun charsSungAt(positionMs: Long): Float {
        if (positionMs <= startMs) return 0f
        if (positionMs >= endMs) return text.length.toFloat()
        if (syllables.isEmpty()) {
            val span = (endMs - startMs).coerceAtLeast(1L)
            return (positionMs - startMs).toFloat() / span * text.length
        }
        for (syllable in syllables) {
            if (positionMs < syllable.startMs) return syllable.startChar.toFloat()
            if (positionMs < syllable.endMs) {
                val span = (syllable.endMs - syllable.startMs).coerceAtLeast(1L)
                val through = (positionMs - syllable.startMs).toFloat() / span
                return syllable.startChar + through * (syllable.endChar - syllable.startChar)
            }
        }
        return text.length.toFloat()
    }

    /**
     * This word with every character [drop] matches taken out of its text, and
     * the syllables moved to keep covering the same letters. Null if nothing
     * is left.
     */
    fun withoutChars(drop: (Char) -> Boolean): LyricWord? {
        if (text.none(drop)) return this
        // Where each old offset lands once the dropped characters are gone.
        val moved = IntArray(text.length + 1)
        val kept = StringBuilder()
        text.forEachIndexed { index, char ->
            moved[index] = kept.length
            if (!drop(char)) kept.append(char)
        }
        moved[text.length] = kept.length
        val lead = kept.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return null
        val trimmed = kept.toString().trim()
        val parts = syllables.map {
            LyricSyllable(
                startMs = it.startMs,
                endMs = it.endMs,
                startChar = (moved[it.startChar] - lead).coerceAtLeast(0),
                endChar = (moved[it.endChar] - lead).coerceAtMost(trimmed.length),
            )
        }
        return copy(text = trimmed, syllables = LyricSyllable.normalized(parts, trimmed.length))
    }
}

/**
 * One separately timed piece of a [LyricWord]: the characters [startChar]
 * until [endChar] of the word's text, sung from [startMs] to [endMs].
 */
data class LyricSyllable(
    val startMs: Long,
    val endMs: Long,
    val startChar: Int,
    val endChar: Int,
) {
    companion object {
        /**
         * Tidies a word's syllables, as a parser collected them, into what
         * [LyricWord.charsSungAt] expects: in order, end to end with no
         * characters between them, and covering the whole of a
         * [length]-character word. Punctuation hanging off the last syllable
         * belongs to it, and an untimed apostrophe between two goes to the
         * first.
         *
         * Empty when there is nothing to split — one syllable is just the word.
         */
        fun normalized(parts: List<LyricSyllable>, length: Int): List<LyricSyllable> {
            val usable = parts
                .filter { it.endChar > it.startChar && it.startChar < length }
                .sortedBy { it.startChar }
            if (usable.size < 2) return emptyList()
            return usable.mapIndexed { index, part ->
                LyricSyllable(
                    startMs = part.startMs,
                    endMs = maxOf(part.startMs, part.endMs),
                    startChar = if (index == 0) 0 else part.startChar,
                    endChar = usable.getOrNull(index + 1)?.startChar ?: length,
                )
            }
        }
    }
}

/**
 * Which side of the panel a line is sung from.
 *
 * A duet is written in TTML as a `ttm:agent` per line, and Apple lays the two
 * voices out on opposite sides so a call-and-response reads as two people
 * rather than one long verse. [Start] is the default and the only side a
 * single-voice song ever uses — see `TtmlLyrics` for how the sides are worked
 * out from the agents, which is not simply "one agent per side".
 */
enum class LyricAlignment { Start, End }

/**
 * One synced line. [timeMs] is when it starts; a blank [text] is an
 * instrumental stretch — LRC files mark those with a bare timestamp.
 *
 * [words] is populated only by the providers that carry word-level timing
 * (BetterLyrics, LyricsPlus, SimpMusic's rich sync). LRCLIB has none, so a
 * line from there highlights whole; see [isWordSynced].
 *
 * [sungUntilMs] is the line's own end where a line-synced provider states one,
 * which is what lets an interlude be told apart from a slowly sung line.
 *
 * [background] is the answering vocal — the "(ooh)" or the echoed half-phrase
 * a second voice sings over the lead. It is a line in its own right, with its
 * own stamp and its own words, because that is what it is: it starts partway
 * through the line it answers and routinely runs past the *next* line's stamp.
 * Run into [text] it dragged the sweep along with it, and the cursor — which
 * takes the last line whose stamp has passed — moved on before the bracket had
 * been sung, so the tail of the line was skipped. Kept apart it draws
 * underneath the lead on its own clock. Never nested: a background line's own
 * [background] is always null.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    val background: LyricLine? = null,
    val alignment: LyricAlignment = LyricAlignment.Start,
    /** Display-only translation: drive its sweep and bloom from the original vocal. */
    val timingSource: LyricLine? = null,
) {
    val isGap: Boolean get() = text.isEmpty()

    val isWordSynced: Boolean get() = words.isNotEmpty()

    /**
     * Where each of [words] sits in [text], as character ranges.
     *
     * Walked from where the last one ended rather than searched from the start,
     * so a word repeated in the line lines up with its own occurrence — the
     * same walk [revealedChars] does, kept once because the rise reads it on
     * every frame of the line being sung rather than once as it passes.
     */
    val wordSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        var offset = 0
        words.map { word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            offset = end
            start until end
        }
    }

    /**
     * Where each separately timed piece of the line sits in [text]: a word's
     * syllables where the provider split it, the word itself where it did not.
     * The sweep crosses each of these at a steady pace of its own.
     */
    val sweepSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        words.flatMapIndexed { index, word ->
            val span = wordSpans[index]
            if (word.syllables.isEmpty()) {
                listOf(span)
            } else {
                word.syllables.map { (span.first + it.startChar) until (span.first + it.endChar) }
            }
        }
    }

    /**
     * Whether anything on this line is off the floor at [positionMs].
     *
     * Two comparisons, so the lines that are not being sung — which is every
     * line but one, on every frame — settle the question without walking their
     * words to find out that none of them have moved.
     */
    fun isLifted(positionMs: Long): Boolean {
        val first = words.firstOrNull() ?: return false
        if (positionMs <= first.startMs) return false
        // A held word's swell runs on past the ordinary fall — its last letter
        // is still easing back down for a while after the word ends.
        // Stopping at the fall would snap that letter back to its laid-out
        // size mid-swell.
        return positionMs < words.last().endMs + RISE_MS || isGrowing(positionMs)
    }

    /**
     * The stretch of the song over which anything drawn for this line moves —
     * the sweep, the lift and its settle, a held word's letters — from
     * [animatesFromMs] to [animatesUntilMs]. Before it the line is drawn
     * unsung, after it sung and at rest, and either way exactly the same on
     * every frame.
     *
     * Which is what lets a line outside it stop listening to the clock. Every
     * line on screen reading it redrew on every frame of playback — and the
     * blur on the lines away from the playing one then had to be worked out
     * again each time, for a picture that had not changed.
     */
    val animatesFromMs: Long by lazy(LazyThreadSafetyMode.NONE) {
        minOf(timeMs, words.firstOrNull()?.startMs ?: timeMs)
    }

    /** See [animatesFromMs]. */
    val animatesUntilMs: Long by lazy(LazyThreadSafetyMode.NONE) {
        val settled = words.lastOrNull()?.let { it.endMs + RISE_MS.toLong() } ?: endMs
        val grown = growingWords.maxOfOrNull { it.restsAtMs } ?: Long.MIN_VALUE
        maxOf(endMs, settled, grown)
    }

    /**
     * How far the word covering [positionMs] has lifted, 0..1.
     *
     * Apple Music's words don't only light up, they rise as they land and
     * settle back once they are past — the lift travelling along the line is
     * most of what separates singing from a bar sliding across the text.
     *
     * How high a word goes is set by how long it is held — see [liftHeight]:
     * patter barely leaves the line and a carried note goes all the way up,
     * the same "a note carried is worth more than a note rattled off" that
     * shapes the glow. It rises over its own length, so a quick word's small
     * lift is also a quick one, and settles back over [RISE_MS] from its end.
     */
    fun wordLift(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val riseMs = (word.endMs - word.startMs).toFloat().coerceIn(RISE_MIN_MS, RISE_MS)
        val rising = ((positionMs - word.startMs) / riseMs).coerceIn(0f, 1f)
        val falling = (1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f)
        return liftHeight(word) * smooth(minOf(rising, falling))
    }

    /**
     * Whether anything actually told us when the singing stops, rather than
     * only when it starts. Word timings carry it, and so does a provider that
     * stamps the line's own end ([sungUntilMs]).
     *
     * The distance to the next line's stamp is *not* evidence of an end: that
     * distance is the line's own slot, and on a line-synced source it is
     * routinely ten seconds for a line sung over all ten of them.
     */
    val hasKnownEnd: Boolean get() = words.isNotEmpty() || sungUntilMs != null

    /**
     * When the last word finishes — or the line's own end where the provider
     * gave one, or [timeMs] when nothing did. Check [hasKnownEnd] before
     * reading a silence out of this.
     *
     * The answering vocal counts: it is still this line being sung, and it
     * regularly holds a note past the lead's last word. Measured without it, a
     * break would be found in the middle of a line that is still going.
     */
    val endMs: Long
        get() {
            val lead = words.lastOrNull()?.endMs ?: sungUntilMs ?: timeMs
            return maxOf(lead, background?.endMs ?: lead)
        }

    /**
     * How far through the line the singing has got, 0..1, as a fractional
     * index into [text]. The sweep reveals up to this character.
     *
     * Within a word it interpolates across that word's own span, so a held
     * note draws slowly and a rattled-off one snaps. Whitespace between two
     * words is credited to the gap between them: it fills as the singer moves
     * on rather than jumping ahead of the next word's first letter.
     */
    fun revealedChars(positionMs: Long): Float {
        timingSource?.let { source ->
            if (source.text.isEmpty()) return 0f
            return (source.revealedChars(positionMs) / source.text.length)
                .coerceIn(0f, 1f) * text.length
        }
        if (words.isEmpty()) return if (positionMs >= timeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            // Where this word sits in [text]. Built by walking rather than
            // searching, so a word repeated in the line still lines up.
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) return start + word.charsSungAt(positionMs)
            // Past this word: the trailing space fills over the pause before
            // the next one, so the highlight keeps creeping instead of resting
            // on the word's last letter.
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    /**
     * The words held long enough to be worth animating letter by letter.
     *
     * A word carried for a second or more is the one moment in a line where
     * there is time to see anything happen to it, and drawing it the same as
     * the syllables either side wastes that: it is what makes a held note in
     * "golden hour" read as a pause in the highlight rather than as the singer
     * holding on. Each letter of one swells and lights in turn — see
     * [GrowingWord] for the shape of it, which is Apple Music's.
     *
     * Rare by design. Most lines have none at all and pay a walk of their words
     * once, on the first frame they are drawn.
     */
    val growingWords: List<GrowingWord> by lazy(LazyThreadSafetyMode.NONE) {
        words.mapIndexedNotNull { index, word ->
            GrowingWord.of(index, word)
        }
    }

    /** The letter-by-letter treatment for word [index], where it has earned one. */
    fun growingAt(index: Int): GrowingWord? =
        growingWords.firstOrNull { it.index == index }

    /**
     * Whether any word on this line is mid-flight at [positionMs]. Answered
     * without touching the words themselves, so the lines that have no held
     * word — which is most of them — settle it in a comparison.
     */
    fun isGrowing(positionMs: Long): Boolean = growingWords.any {
        positionMs >= it.startMs && positionMs <= it.restsAtMs
    }

    /**
     * When this line's answering voice takes the row over from the lead, or
     * null when it never does — which is nearly every line.
     *
     * Apple Music gives a backing vocal the row when it is carried long after
     * the lead has finished: the "ooh" held under the end of a chorus, the
     * echoed word that rings on for bars. Drawn small under a lead that has
     * already been sung, it is the one thing on screen still being sung and
     * the smallest thing on it. So once the lead ends the lead fades away and
     * the answering voice grows into its place at more than lead size, and
     * when it ends the lead comes back. See [BackingPromotion] for the
     * qualifying rule and the motion, both Apple's.
     *
     * Worked out once, on the first frame the line is drawn: one walk of each
     * voice's words. A line that does not qualify is a null from then on,
     * which is all the panel ever asks of it.
     */
    val backingPromotion: BackingPromotion? by lazy(LazyThreadSafetyMode.NONE) {
        BackingPromotion.of(this)
    }
}

/**
 * One word held long enough to be animated a letter at a time.
 *
 * Apple Music's held-word emphasis, number for number. Every letter runs the
 * same envelope — ease up to a swell, hold it, ease back to the size it was laid
 * out at — over a window the length of the word itself. Letter i's window opens
 * [letterDelay] i times over after the word starts and closes as far after the
 * word ends, so the swell travels along the word instead of the whole of it
 * pulsing at once, and the tail is still letting go after the singer has moved
 * on, which is what makes the held note read as having been carried.
 *
 * How far a letter swells is set by the hold alone: nothing at one second,
 * rising to [GROW_SCALE] at two and staying there. Apple uses the same envelope
 * for the bloom behind the letter, so the light and the swell arrive and leave
 * together rather than as two movements that happen to overlap. The lift is not
 * part of it: a held word rises and settles with the rest of the line, see
 * [LyricLine.wordLift].
 *
 * Everything that depends only on the word — its letters, its timing, the shape
 * of the envelope — is worked out here once. A frame only asks where along the
 * envelope each letter has got to, which is a handful of comparisons and one
 * curve lookup per letter.
 */
class GrowingWord internal constructor(
    /** Which of the line's words this is. */
    val index: Int,
    word: LyricWord,
    /**
     * Where each letter starts in the word's text, plus the text's length at
     * the end: letter i is the characters `bounds[i] until bounds[i + 1]`.
     */
    private val bounds: IntArray,
) {
    val startMs: Long = word.startMs
    val endMs: Long = word.endMs

    /** How many letters — graphemes, so an accented letter or an emoji is one. */
    val letters: Int get() = bounds.size - 1

    /** Where letter [letter] starts, as a character offset into the word's text. */
    fun letterStart(letter: Int): Int = bounds[letter]

    /** Where letter [letter] ends, exclusive, as a character offset into the word's text. */
    fun letterEnd(letter: Int): Int = bounds[letter + 1]

    private val heldMs: Float = (endMs - startMs).coerceAtLeast(1L).toFloat()

    /** How long each letter takes to ease all the way up, and all the way back. */
    private val growMs: Float = minOf(heldMs, GROW_EASE_MAX_MS)

    /**
     * How long after its own window opens a letter starts to let go. A short
     * word holds its letters at the top for longer; a long word hands each
     * one back sooner, so the last is not still swollen long after the line.
     */
    private val returnAfterMs: Float = heldMs * holdRatio(heldMs) / letters

    /** Where the envelope has got to when a letter starts to let go. */
    private val peak: Float = contentCurve((returnAfterMs / growMs).coerceIn(0f, 1f))

    /** How far apart the letters' windows open. */
    private val letterDelayMs: Float = letterDelay(heldMs, letters)

    /**
     * The most a letter of this word swells, as a share of its laid-out size —
     * 0 for a word held for exactly a second, [GROW_SCALE] from two seconds on.
     */
    val amount: Float = (holdRatio(heldMs) - 1f) * GROW_SCALE

    /** When the last letter has finished letting go and the word is at rest. */
    val restsAtMs: Long =
        startMs + ceil((letters - 1) * letterDelayMs + returnAfterMs + growMs).toLong()

    /**
     * How far through its envelope letter [letter] is at [positionMs], 0..1:
     * Apple Music's `emphasis`, with the letter's window already shifted.
     */
    fun emphasisAt(letter: Int, positionMs: Long): Float {
        val elapsed = positionMs - startMs - letter * letterDelayMs
        if (elapsed < 0f) return 0f
        if (elapsed <= returnAfterMs) return contentCurve((elapsed / growMs).coerceIn(0f, 1f))
        val letting = ((elapsed - returnAfterMs) / growMs).coerceIn(0f, 1f)
        return peak * (1f - contentCurve(letting))
    }

    /**
     * Where every letter of the word is at [positionMs], written into [into]
     * rather than returned: this runs for every letter of the word on every
     * frame, three times over — dim copy, lit copy and bloom — and a line's
     * worth of short-lived objects per frame is a cost with nothing to show for
     * it.
     *
     * Reads [WordSwell.width], which the caller fills in first with each
     * letter's laid-out width in pixels: how far the neighbours have to make way
     * depends on how wide the letters doing the swelling actually are. [rtl] is
     * whether letter 0 sits at the right-hand end of the word on screen.
     */
    fun sampleInto(positionMs: Long, rtl: Boolean, into: WordSwell) {
        for (letter in 0 until letters) {
            val through = emphasisAt(letter, positionMs)
            val grown = through * amount
            into.scale[letter] = 1f + grown
            // Apple derives the bloom from the swell, so a word that does not
            // swell — one held for exactly a second — does not light up either.
            into.glow[letter] = if (amount > 0f) through else 0f
            into.halfGrowth[letter] = into.width[letter] * grown / 2f
        }
        spreadLetters(into.halfGrowth, letters, rtl, into.shift)
    }

    internal companion object {
        /**
         * The treatment for [word], or null where it has not earned one — see
         * [allowsEmphasis]. Ordered cheapest first, because this is asked of
         * every word of every line and almost all of them fail on the hold.
         */
        fun of(index: Int, word: LyricWord): GrowingWord? {
            if (word.endMs - word.startMs < GROW_MIN_MS) return null
            if (word.text.isEmpty() || !allowsEmphasis(word.text)) return null
            val bounds = letterBounds(word.text) ?: return null
            return GrowingWord(index, word, bounds)
        }
    }
}

/**
 * Where each letter of one [GrowingWord] is on one frame, filled in by
 * [GrowingWord.sampleInto]. Indexed by letter.
 *
 * Held by the caller and reused frame after frame, sized for the longest word
 * that can qualify, so drawing a held word allocates nothing.
 */
class WordSwell {
    /** Each letter's laid-out width in pixels. Filled in by the caller. */
    val width = FloatArray(GROW_MAX_LETTERS)

    /** Swell, about the letter's own centre. 1 is the letter as laid out. */
    val scale = FloatArray(GROW_MAX_LETTERS)

    /**
     * How far the letter moves sideways to make room for the swell, in the
     * same pixels as [width]: away from the middle of the word, so it opens
     * outwards while the words either side stay where they are.
     */
    val shift = FloatArray(GROW_MAX_LETTERS)

    /** Bloom, 0..1: the same envelope as the swell. */
    val glow = FloatArray(GROW_MAX_LETTERS)

    /** How far each side of a letter grows past its laid-out box. Working space. */
    internal val halfGrowth = FloatArray(GROW_MAX_LETTERS)
}

/** How long a word takes to rise, and to settle back down once it is past. */
private const val RISE_MS = 700f

/** The quickest a word ever rises: a 90 ms syllable snapping up reads as a twitch. */
private const val RISE_MIN_MS = 250f

/**
 * How far up a word goes, as a share of the full lift, from how long it is held.
 *
 * A word sung in [LIFT_QUICK_MS] or less only nudges up by [LIFT_FLOOR]; one
 * held for [LIFT_HELD_MS] or more goes the whole way. Eased between the two, so
 * the words of an ordinary line differ from one another by a little rather than
 * falling into "up" and "not up".
 */
private fun liftHeight(word: LyricWord): Float {
    val held = (word.endMs - word.startMs).toFloat()
    val through = ((held - LIFT_QUICK_MS) / (LIFT_HELD_MS - LIFT_QUICK_MS)).coerceIn(0f, 1f)
    return LIFT_FLOOR + (1f - LIFT_FLOOR) * smooth(through)
}

private const val LIFT_QUICK_MS = 150f
private const val LIFT_HELD_MS = 900f
private const val LIFT_FLOOR = 0.25f

/** Ease in and out of the ends, so the lift has no corners on it. */
private fun smooth(fraction: Float) = fraction * fraction * (3f - 2f * fraction)

/**
 * Whether [text] is written in a script whose letters can be swelled one at a
 * time — Apple Music's own test, code point for code point.
 *
 * Han, kana and Hangul draw as blocks, and the scripts from Hebrew through to
 * Myanmar join or stack their letters, so moving one letter of a word in any of
 * them would come apart rather than swell.
 */
internal fun allowsEmphasis(text: String): Boolean {
    var at = 0
    while (at < text.length) {
        val point = text.codePointAt(at)
        if (point in 0x0590..0x109F || point in 0x3040..0x30FF ||
            point in 0x3400..0x9FFF || point in 0xAC00..0xD7AF
        ) {
            return false
        }
        at += Character.charCount(point)
    }
    return true
}

/**
 * Where each letter of [text] starts, as character offsets, with the text's
 * length on the end; null past [GROW_MAX_LETTERS] letters.
 *
 * Letters are graphemes rather than chars, as they are in Apple's renderer: an
 * emoji is two chars and an accent can be a char of its own, and swelling half
 * of either draws a broken glyph.
 */
private fun letterBounds(text: String): IntArray? {
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(text)
    val found = IntArray(GROW_MAX_LETTERS + 1)
    var count = 0
    var at = breaks.first()
    while (at != BreakIterator.DONE) {
        if (count > GROW_MAX_LETTERS) return null
        found[count++] = at
        at = breaks.next()
    }
    // The first boundary and the last: a word of one letter has two.
    if (count < 2) return null
    return found.copyOf(count)
}

/**
 * How much longer than its own window a letter holds before letting go, from
 * how long the word is held: one second earns nothing extra, two earn double.
 * Apple Music reads the same ratio for how far the letters swell.
 */
private fun holdRatio(heldMs: Float): Float = (heldMs / 1_000f).coerceIn(1f, 2f)

/**
 * How far apart, in milliseconds, the letters' windows open: two fifths of
 * each letter's share of the hold, and never more than [GROW_LETTER_DELAY_MAX_MS]
 * — past that a long-held word's swell would still be crawling along it well
 * into the next line.
 */
private fun letterDelay(heldMs: Float, letters: Int): Float =
    minOf(GROW_LETTER_DELAY_SHARE * heldMs / letters.coerceAtLeast(1), GROW_LETTER_DELAY_MAX_MS)

/**
 * How far sideways each swelling letter moves, in the units of [halfGrowth],
 * written into [into]: Apple Music's own spread, ported as is.
 *
 * Each letter grows by [halfGrowth] on either side of its own centre. The
 * middle of the word — a letter for an odd count, the gap between two for an
 * even one — stays where it is, and every letter further out moves away from
 * it by enough to clear the ones between, so the word opens out from its middle
 * and the words either side of it are left exactly where they were laid out.
 * [rtl] flips which way is "out" for a word whose first letter is on the right.
 */
internal fun spreadLetters(halfGrowth: FloatArray, count: Int, rtl: Boolean, into: FloatArray) {
    for (i in 0 until count) into[i] = 0f
    if (count <= 1) return
    val even = count % 2 == 0
    val centre = if (even) count / 2f - 0.5f else (count / 2).toFloat()
    val left = if (even) floor(centre).toInt() else centre.toInt() - 1
    val right = if (even) ceil(centre).toInt() else centre.toInt() + 1
    val outLeft = if (rtl) 0.5f else -0.5f
    for (i in left downTo 0) {
        var reach = halfGrowth[i]
        if (i + 1 <= centre) reach += halfGrowth[i + 1]
        if (i + 1 < centre) reach += abs(into[i + 1])
        into[i] = outLeft * reach
    }
    for (i in right until count) {
        var reach = halfGrowth[i]
        if (i - 1 >= centre) reach += halfGrowth[i - 1]
        if (i - 1 > centre) reach += abs(into[i - 1])
        into[i] = -outLeft * reach
    }
}

/**
 * Apple Music's content curve, `cubic-bezier(0.25, 0.1, 0.25, 1)` — the CSS
 * `ease` — which every letter's swell rises and falls on.
 *
 * Solved here rather than borrowed from Compose because this module has no
 * Compose in it: a few Newton steps for the x the curve is at, falling back to
 * halving the interval on the rare start where Newton's slope is too flat to
 * trust.
 */
internal fun contentCurve(fraction: Float): Float =
    cubicBezierCurve(fraction, CURVE_X1, CURVE_Y1, CURVE_X2, CURVE_Y2)

/**
 * The CSS `cubic-bezier(x1, y1, x2, y2)` easing at [fraction] — [contentCurve]
 * and any other of Apple Music's curves this module has to run without
 * Compose. See [contentCurve] for how it is solved.
 */
internal fun cubicBezierCurve(fraction: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (fraction <= 0f) return 0f
    if (fraction >= 1f) return 1f
    var t = fraction
    repeat(8) {
        val error = bezier(t, x1, x2) - fraction
        if (abs(error) < 1e-5f) return bezier(t, y1, y2)
        val slope = bezierSlope(t, x1, x2)
        if (abs(slope) < 1e-6f) return@repeat
        t -= error / slope
    }
    var low = 0f
    var high = 1f
    t = t.coerceIn(0f, 1f)
    repeat(30) {
        val x = bezier(t, x1, x2)
        if (abs(x - fraction) < 1e-5f) return bezier(t, y1, y2)
        if (x < fraction) low = t else high = t
        t = (low + high) / 2f
    }
    return bezier(t, y1, y2)
}

/** One coordinate of a cubic Bézier from (0, 0) to (1, 1), at parameter [t]. */
private fun bezier(t: Float, p1: Float, p2: Float): Float {
    val u = 1f - t
    return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
}

/** How fast [bezier] is moving at [t]. */
private fun bezierSlope(t: Float, p1: Float, p2: Float): Float {
    val u = 1f - t
    return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2)
}

private const val CURVE_X1 = 0.25f
private const val CURVE_Y1 = 0.1f
private const val CURVE_X2 = 0.25f
private const val CURVE_Y2 = 1f

/**
 * The shortest hold that earns a word the treatment, Apple Music's one second.
 * Anything quicker is being sung at an ordinary pace and has nothing to show.
 */
private const val GROW_MIN_MS = 1_000L

/**
 * The longest word worth running a swell along — Apple Music's seven letters.
 * Past this it reads as a ripple rather than as one word being held.
 */
internal const val GROW_MAX_LETTERS = 7

/**
 * How much bigger a letter gets at the top of its swell, for a word held two
 * seconds or longer: Apple Music's 14%. A word held for less gets a share of it,
 * down to nothing at one second; see [GrowingWord.amount].
 */
internal const val GROW_SCALE = 0.14f

/** The longest a letter ever takes to ease up, or back: Apple Music's three seconds. */
private const val GROW_EASE_MAX_MS = 3_000f

/** See [letterDelay]: Apple Music's two fifths, capped at 400 ms. */
private const val GROW_LETTER_DELAY_SHARE = 0.4f
private const val GROW_LETTER_DELAY_MAX_MS = 400f
