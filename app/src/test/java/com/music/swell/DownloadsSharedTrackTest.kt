package com.music.swell

import com.music.swell.download.Downloads
import com.music.swell.download.SavedCollection
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSharedTrackTest {

    private fun record(id: String, vararg ids: String) =
        SavedCollection(id = id, title = id, playlist = true, videoIds = ids.toList())

    @Test
    fun `deleting a release keeps the tracks another release lists`() {
        val collections = mapOf(
            "one" to record("one", "a", "b", "c"),
            "two" to record("two", "b"),
        )
        val saved = mapOf("a" to "file:a", "b" to "file:b", "c" to "file:c")
        assertEquals(listOf("a", "c"), Downloads.tracksOnlyIn("one", collections, saved))
    }

    @Test
    fun `one file under two ids counts as shared`() {
        // "video" was swapped for "track" on the way down: both name one file.
        val collections = mapOf(
            "one" to record("one", "video", "a"),
            "two" to record("two", "track"),
        )
        val saved = mapOf("video" to "file:x", "track" to "file:x", "a" to "file:a")
        assertEquals(listOf("a"), Downloads.tracksOnlyIn("one", collections, saved))
    }
}
