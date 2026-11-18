package com.music.swell.ui.player

import com.music.swell.data.lyrics.BackingPromotion
import com.music.swell.data.lyrics.LyricLine

/** Keep every unfinished vocal visible, including overlaps spanning more than two rows. */
internal fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    val latest = lines.indexOfLast { it.timeMs <= positionMs }
    if (latest < 0) return emptyList()
    return (0..latest).filter { index ->
        val line = lines[index]
        index == latest || (!line.isGap &&
            (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
            line.timeMs <= positionMs && positionMs < line.endMs)
    }
}

/**
 * The row whose backing vocal has taken it over at [positionMs] — see
 * [LyricLine.backingPromotion] — or -1 when none has.
 *
 * That row holds the panel's focus for as long as the backing has it, the way
 * Apple Music keeps a promoted row where it is while newer lines sing beneath
 * it: it is the biggest thing on screen and the one still being sung, and the
 * panel moving on from it on the lead's run-up to the next line would carry it
 * up out of view mid-note. Only from the backing's last word does the panel
 * move on as it otherwise would.
 *
 * [rows] lists the rows that have a promotion at all, top to bottom, so a frame
 * costs a comparison or two per promoted row — usually none, never many —
 * rather than a walk of the song. Should two promoted rows overlap, the upper
 * one holds, as the uppermost unfinished vocal does everywhere else.
 */
internal fun promotedHoldRow(
    promotions: List<BackingPromotion?>,
    rows: IntArray,
    positionMs: Long,
): Int {
    for (row in rows) {
        if (promotions[row]?.isPromoted(positionMs) == true) return row
    }
    return -1
}
