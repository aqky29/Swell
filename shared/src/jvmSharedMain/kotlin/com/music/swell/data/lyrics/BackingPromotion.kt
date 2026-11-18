package com.music.swell.data.lyrics

import kotlin.math.max

/**
 * A line whose answering voice is carried long enough after the lead to take
 * the row over — see [LyricLine.backingPromotion] for why Apple Music does it.
 *
 * Three moments drive the whole thing. Up to [mainEndMs] the row is the row it
 * always was: the lead, and the backing opening small underneath it. From
 * [mainEndMs] the lead fades up and away while the backing grows into its place
 * over [ENTER_MS], to [SCALE] times the lead's own size, and the row stays like
 * that — the backing is now the line being sung — until [backingEndMs]. Then,
 * over [EXIT_MS], the backing lets go and the lead settles back in.
 *
 * Every number here is Apple Music's lyric renderer's. The motion is read off
 * the song position rather than run as an animation, by [frame], so a pause
 * holds it exactly where it is and a seek lands on the frame that belongs to
 * that moment — there is nothing in flight to finish or to get out of step.
 */
data class BackingPromotion internal constructor(
    /** When the last word of the lead is done: the backing takes over here. */
    val mainEndMs: Long,
    /** When the backing's first word starts. */
    val backingStartMs: Long,
    /** When the backing's last word is done: the lead comes back here. */
    val backingEndMs: Long,
) {
    /** Whether the backing has the row at [positionMs] — it grows in, then holds. */
    fun isPromoted(positionMs: Long): Boolean =
        positionMs >= mainEndMs && positionMs < backingEndMs

    /** Whether the backing is handing the row back to the lead at [positionMs]. */
    fun isLeaving(positionMs: Long): Boolean =
        positionMs >= backingEndMs && positionMs < backingEndMs + EXIT_MS

    /**
     * The position this row is drawn at when the song is at [positionMs]: the
     * position itself while anything about the row is moving, and one fixed
     * moment for each stretch where nothing is.
     *
     * Those stretches are most of the row's life — the seconds before its
     * backing opens, the backing held at full size, the time after it has gone
     * — and on all of them the row's frame is identical from one millisecond
     * to the next. Read through this, the layout that sizes the row only hears
     * the clock while the row is changing size; read straight off the clock it
     * would be laid out again on every frame of the song for a row that had
     * not moved.
     *
     * [revealFromMs] and [revealMs] are when the small backing starts to open
     * and how long it takes; [heldForMs] is as in [frame].
     */
    fun restingPoint(positionMs: Long, revealFromMs: Long, revealMs: Long, heldForMs: Long): Long {
        val revealedAt = revealFromMs + revealMs
        return when {
            positionMs < mainEndMs -> when {
                positionMs < revealFromMs -> minOf(revealFromMs, mainEndMs) - 1
                positionMs >= revealedAt -> revealedAt
                else -> positionMs
            }
            positionMs < backingEndMs ->
                if (positionMs >= mainEndMs + ENTER_MS) mainEndMs + ENTER_MS else positionMs
            positionMs < backingEndMs + EXIT_MS -> positionMs
            heldForMs > EXIT_MS && positionMs < backingEndMs + heldForMs -> backingEndMs + EXIT_MS
            else -> backingEndMs + max(EXIT_MS, heldForMs)
        }
    }

    /**
     * Where everything in the row is at [positionMs], written into [into] —
     * Apple Music's promotion, phase by phase.
     *
     * The row is three blocks laid out by the caller, each measured at its own
     * full size: the lead ([lead] tall, with anything hung under it), the small
     * backing ([small] tall, its text flush with its top and [room] of inset
     * under it) and the backing set at [SCALE] times the lead ([big] tall, with
     * [room] of inset all round, as the lead has). [smallScale] is the small
     * backing's type size over the big one's. [reveal] is how far the small
     * backing has opened, 0..1.
     *
     * The big block is laid out once and scaled rather than set at a size that
     * changes, so the words never re-wrap partway through the growth. Where the
     * two sizes do wrap to a different number of rows anyway — or the small
     * block carries something the big one does not — scaling one into the
     * other would show the words jumping between rows, so with [crossFade] the
     * small block fades out where the big one fades in instead.
     *
     * [heldForMs] is how long after [backingEndMs] the row keeps the room the
     * big backing took, 0 to give it back as the lead returns. A row held past
     * its exit draws its content against the bottom of that room — see
     * [PromotionFrame.shift] — so the row seems to shrink from the top, towards
     * the rows under it, while its laid-out height stays put.
     *
     * Heights are in whatever unit the caller measured in; the small offsets
     * and blurs are in dp, as Apple's are.
     */
    fun frame(
        positionMs: Long,
        reveal: Float,
        lead: Float,
        small: Float,
        big: Float,
        room: Float,
        smallScale: Float,
        crossFade: Boolean,
        heldForMs: Long,
        into: PromotionFrame,
    ) {
        into.reset()
        when {
            positionMs < mainEndMs -> {
                // The row as any other: the lead, and the backing opening small
                // under it on its own clock.
                into.phase = PromotionFrame.BEFORE
                into.height = lead + small * reveal
                into.reveal = reveal
                into.smallTop = lead
                into.smallAlpha = 1f
            }
            positionMs < backingEndMs -> {
                // The lead goes up and out of focus as the backing grows up
                // into its place. A backing that only starts after the lead
                // has finished comes in with the growth rather than after it.
                val grow = contentCurve((positionMs - mainEndMs).toFloat() / ENTER_MS)
                val show = 1f - grow
                val alpha = max(reveal, grow)
                val scale = smallScale + (1f - smallScale) * grow
                // The backing's first row of text travels from just under the
                // lead to where the lead's own first row sits.
                val textTop = lead + (room - lead) * grow
                val bottom = (lead + small) + (big - lead - small) * grow
                val mix = if (crossFade) show else 0f
                into.phase = PromotionFrame.PROMOTED
                into.grow = grow
                into.height = show * lead + (bottom - show * lead) * alpha
                into.leadAlpha = show
                into.leadRise = -LEAD_RISE_DP * grow
                into.leadBlur = LEAD_BLUR_DP * grow
                into.bigTop = textTop - room
                into.bigAlpha = alpha * (1f - mix)
                into.bigScale = scale
                into.smallTop = textTop
                into.smallAlpha = alpha * mix
                into.smallScale = scale / smallScale
                into.reveal = 1f
            }
            positionMs < backingEndMs + EXIT_MS -> {
                // The backing steps back and goes soft as the lead settles in
                // from just below where it left.
                val ease = cubicBezierCurve(
                    (positionMs - backingEndMs).toFloat() / EXIT_MS,
                    EXIT_X1, EXIT_Y1, EXIT_X2, EXIT_Y2,
                )
                val visible = big + (lead - big) * ease
                into.phase = PromotionFrame.LEAVING
                into.grow = 1f
                into.height = if (heldForMs > 0L) big else visible
                into.shift = into.height - visible
                into.leadAlpha = ease
                into.leadRise = EXIT_TRAVEL_DP * (1f - ease)
                into.leadBlur = EXIT_BLUR_DP * (1f - ease)
                into.bigAlpha = 1f - ease
                into.bigScale = 1f - EXIT_SHRINK * ease
                into.bigRise = -EXIT_TRAVEL_DP * ease
                into.bigBlur = EXIT_BLUR_DP * ease
            }
            else -> {
                // The lead alone, as it was before the backing ever opened.
                val held = heldForMs > 0L && positionMs < backingEndMs + heldForMs
                into.phase = PromotionFrame.AFTER
                into.height = if (held) big else lead
                into.shift = into.height - lead
            }
        }
    }

    companion object {
        /**
         * How long the backing has to be sung, first word to last, before it
         * can take the row: Apple Music's six seconds. Shorter, it is an
         * answer to the lead rather than a voice carrying on after it.
         */
        const val MIN_MS = 6_000L

        /** How big the backing grows, against the lead's own type: Apple's 1.3. */
        const val SCALE = 1.3f

        /**
         * How long the backing takes to grow into the lead's place — and how
         * long past the lead it has to run for there to be anything to grow
         * into. Apple Music's 550 ms, on its content curve.
         */
        const val ENTER_MS = 550L

        /** How long the lead takes to come back once the backing ends: Apple's 800 ms. */
        const val EXIT_MS = 800L

        /**
         * Whether [line] qualifies, and when: Apple Music's rule. Both voices
         * need word timings — without them there is no telling when either
         * one stops. Blank words carry no singing and are not counted.
         */
        internal fun of(line: LyricLine): BackingPromotion? {
            val backing = line.background ?: return null
            if (!line.isWordSynced || !backing.isWordSynced) return null
            var mainEnd = Long.MIN_VALUE
            for (word in line.words) {
                if (word.text.isNotBlank()) mainEnd = maxOf(mainEnd, word.endMs)
            }
            var backingStart = Long.MAX_VALUE
            var backingEnd = Long.MIN_VALUE
            for (word in backing.words) {
                if (word.text.isBlank()) continue
                backingStart = minOf(backingStart, word.startMs)
                backingEnd = maxOf(backingEnd, word.endMs)
            }
            if (mainEnd == Long.MIN_VALUE || backingEnd == Long.MIN_VALUE) return null
            if (backingEnd - backingStart <= MIN_MS) return null
            // A backing that ends with the lead, or only just after it, has no
            // time of its own to take the row in.
            if (backingEnd - mainEnd < ENTER_MS) return null
            return BackingPromotion(mainEnd, backingStart, backingEnd)
        }
    }
}

/**
 * One frame of a [BackingPromotion], as [BackingPromotion.frame] works it out.
 *
 * Filled in place rather than handed back new: the row asks for one on every
 * frame it is moving, and there is no reason for that to allocate.
 */
class PromotionFrame {
    /** Which part of the promotion this is: [BEFORE], [PROMOTED], [LEAVING] or [AFTER]. */
    var phase: Int = BEFORE

    /** How tall the row is laid out. */
    var height: Float = 0f

    /**
     * How far down the row's content is drawn inside [height]. Only ever more
     * than nothing on a row holding on to its room past the exit: its content
     * sits against the bottom of that room, which is where it will be once the
     * room is given back.
     */
    var shift: Float = 0f

    /** How far the backing has grown into the lead's place, 0..1. */
    var grow: Float = 0f

    var leadAlpha: Float = 1f

    /** How far the lead is drawn below where it is laid out, in dp. */
    var leadRise: Float = 0f

    /** How soft the lead is, as a blur radius in dp. */
    var leadBlur: Float = 0f

    /**
     * How far the small backing has opened, 0..1, before the lead ends. Its
     * look on the way in is the panel's own — see [BEFORE].
     */
    var reveal: Float = 0f

    /** Where the small backing's top is laid out. */
    var smallTop: Float = 0f
    var smallAlpha: Float = 0f

    /** The small backing's size against its own, about the start of its first row. */
    var smallScale: Float = 1f

    /** Where the big backing's top is laid out. */
    var bigTop: Float = 0f
    var bigAlpha: Float = 0f

    /** The big backing's size against its own, about the start of its first row. */
    var bigScale: Float = 1f

    /** How far the big backing is drawn below where it is laid out, in dp. */
    var bigRise: Float = 0f

    /** How soft the big backing is, as a blur radius in dp. */
    var bigBlur: Float = 0f

    internal fun reset() {
        phase = BEFORE
        height = 0f
        shift = 0f
        grow = 0f
        leadAlpha = 1f
        leadRise = 0f
        leadBlur = 0f
        reveal = 0f
        smallTop = 0f
        smallAlpha = 0f
        smallScale = 1f
        bigTop = 0f
        bigAlpha = 0f
        bigScale = 1f
        bigRise = 0f
        bigBlur = 0f
    }

    companion object {
        /**
         * The lead is still being sung: the row looks as it would without any
         * promotion, the small backing opening under the lead on its own.
         */
        const val BEFORE = 0

        /** The backing is growing into the lead's place, or holding it. */
        const val PROMOTED = 1

        /** The backing is handing the row back to the lead. */
        const val LEAVING = 2

        /** All over: the lead alone. */
        const val AFTER = 3
    }
}

/** How far the lead drifts up as it leaves, and how soft it goes: Apple's 8 and 4. */
private const val LEAD_RISE_DP = 8f
private const val LEAD_BLUR_DP = 4f

/**
 * On the way out the backing drifts up and goes soft by Apple's 6 while the
 * lead comes back down out of the same 6, and the backing steps back by 5% —
 * the two pass each other rather than one replacing the other in place.
 */
private const val EXIT_TRAVEL_DP = 6f
private const val EXIT_BLUR_DP = 6f
private const val EXIT_SHRINK = 0.05f

/**
 * Apple Music's exit curve, `cubic-bezier(0.22, 1, 0.36, 1)`: almost all of the
 * change up front, then a long settle — the lead is legible again within a
 * couple of hundred milliseconds and spends the rest arriving.
 */
private const val EXIT_X1 = 0.22f
private const val EXIT_Y1 = 1f
private const val EXIT_X2 = 0.36f
private const val EXIT_Y2 = 1f
