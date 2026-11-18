package com.music.swell.ui.player

import com.music.swell.data.lyrics.LyricLine

/** Time lookup for normalized lyrics, including earlier vocals that overlap the latest row. */
internal class LyricTimingIndex(private val lines: List<LyricLine>) {
    private val sorted = lines.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }
    private val latestKnownEnd = LongArray(lines.size).also { ends ->
        var maximum = Long.MIN_VALUE
        lines.forEachIndexed { index, line ->
            if (!line.isGap && (line.hasKnownEnd || line.background?.hasKnownEnd == true)) {
                maximum = maxOf(maximum, line.endMs)
            }
            ends[index] = maximum
        }
    }

    fun indexAt(positionMs: Long): Int {
        if (!sorted) return lines.indexOfLast { it.timeMs <= positionMs }
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
        }
        return low - 1
    }

    fun activeRowsAt(positionMs: Long): List<Int> {
        if (!sorted) return activeLyricRows(lines, positionMs)
        val latest = indexAt(positionMs)
        if (latest < 0) return emptyList()
        val active = mutableListOf(latest)
        var index = latest - 1
        while (index >= 0 && latestKnownEnd[index] > positionMs) {
            val line = lines[index]
            if (!line.isGap && (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
                positionMs < line.endMs
            ) active.add(index)
            index--
        }
        active.reverse()
        return active
    }

    val hasWordSync: Boolean = lines.any { it.isWordSynced || it.background?.isWordSynced == true }
    val hasGaps: Boolean = lines.any { it.isGap }

    /**
     * True if word sweeps, lifts, blooms, or gap dot progress are actively
     * changing at [positionMs], or if the playhead is currently inside the
     * scroll run-up / lead transition to an upcoming line.
     */
    fun isAnimatingAt(positionMs: Long): Boolean {
        if (lines.isEmpty()) return false

        // Check active and overlapping rows
        val active = activeRowsAt(positionMs)
        for (i in active) {
            val line = lines[i]
            if (line.isGap) {
                val until = lines.getOrNull(i + 1)?.timeMs ?: line.endMs
                if (positionMs in line.timeMs..until) return true
            }
            if (line.isWordSynced && positionMs in line.animatesFromMs..line.animatesUntilMs) {
                return true
            }
            val bg = line.background
            if (bg != null && bg.isWordSynced &&
                positionMs in bg.animatesFromMs..bg.animatesUntilMs
            ) {
                return true
            }
        }

        // Check upcoming line transition / scroll lead
        val curIdx = indexAt(positionMs)
        val nextIdx = curIdx + 1
        if (nextIdx in lines.indices) {
            val nextLine = lines[nextIdx]
            if (nextLine.isWordSynced && positionMs in nextLine.animatesFromMs..nextLine.animatesUntilMs) {
                return true
            }
            val nextBg = nextLine.background
            if (nextBg != null && nextBg.isWordSynced &&
                positionMs in nextBg.animatesFromMs..nextBg.animatesUntilMs
            ) {
                return true
            }
            val lead = scrollLead(lines, positionMs, this)
            val leadStart = nextLine.timeMs - lead
            if (positionMs in leadStart..nextLine.timeMs) {
                return true
            }
        } else if (curIdx < 0 && lines.isNotEmpty()) {
            val firstLine = lines[0]
            val lead = scrollLead(lines, positionMs, this)
            val leadStart = firstLine.timeMs - lead
            if (positionMs in leadStart..firstLine.timeMs) {
                return true
            }
        }

        return false
    }

    /**
     * Computes the maximum safe duration in milliseconds to sleep before the next
     * lyric transition or animation window, capped at [maxIdleMs]. Returns 0 if
     * actively animating.
     */
    fun nextDeadlineDeltaMs(positionMs: Long, maxIdleMs: Long = 250L): Long {
        if (isAnimatingAt(positionMs)) return 0L
        if (lines.isEmpty()) return maxIdleMs

        var earliestEvent = Long.MAX_VALUE

        val curIdx = indexAt(positionMs)
        val nextIdx = curIdx + 1
        if (nextIdx in lines.indices) {
            val nextLine = lines[nextIdx]
            val lead = scrollLead(lines, positionMs, this)
            val leadStart = nextLine.timeMs - lead
            if (leadStart > positionMs) earliestEvent = minOf(earliestEvent, leadStart)
            if (nextLine.timeMs > positionMs) earliestEvent = minOf(earliestEvent, nextLine.timeMs)
            if (nextLine.isWordSynced && nextLine.animatesFromMs > positionMs) {
                earliestEvent = minOf(earliestEvent, nextLine.animatesFromMs)
            }
            val nextBg = nextLine.background
            if (nextBg != null && nextBg.isWordSynced && nextBg.animatesFromMs > positionMs) {
                earliestEvent = minOf(earliestEvent, nextBg.animatesFromMs)
            }
        } else if (curIdx < 0 && lines.isNotEmpty()) {
            val firstLine = lines[0]
            val lead = scrollLead(lines, positionMs, this)
            val leadStart = firstLine.timeMs - lead
            if (leadStart > positionMs) earliestEvent = minOf(earliestEvent, leadStart)
            if (firstLine.timeMs > positionMs) earliestEvent = minOf(earliestEvent, firstLine.timeMs)
            if (firstLine.isWordSynced && firstLine.animatesFromMs > positionMs) {
                earliestEvent = minOf(earliestEvent, firstLine.animatesFromMs)
            }
        }

        val active = activeRowsAt(positionMs)
        for (i in active) {
            val line = lines[i]
            if (line.isGap) {
                val until = lines.getOrNull(i + 1)?.timeMs ?: line.endMs
                if (until > positionMs) earliestEvent = minOf(earliestEvent, until)
            }
            if (line.isWordSynced && line.animatesUntilMs > positionMs) {
                earliestEvent = minOf(earliestEvent, line.animatesUntilMs)
            }
            val bg = line.background
            if (bg != null && bg.isWordSynced && bg.animatesUntilMs > positionMs) {
                earliestEvent = minOf(earliestEvent, bg.animatesUntilMs)
            }
            if (line.endMs > positionMs) earliestEvent = minOf(earliestEvent, line.endMs)
        }

        if (earliestEvent == Long.MAX_VALUE) return maxIdleMs
        val delta = earliestEvent - positionMs
        return delta.coerceIn(1L, maxIdleMs)
    }
}
