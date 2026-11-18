package com.music.swell

import com.music.swell.data.model.EntityType
import com.music.swell.data.model.SearchHistoryEntity
import com.music.swell.data.model.withCorrectedType
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryCorrectedTypeTest {

    @Test
    fun `podcast show saved as a track by an older build no longer plays`() {
        val show = SearchHistoryEntity(
            id = "MPSPPLE0Jo6NF_JYO5-phess8GKafKMtPv3tfZ",
            title = "Huberman Lab",
            subtitle = "Podcast",
            artworkUrl = null,
            entityType = EntityType.TRACK,
        ).withCorrectedType()

        assertEquals(EntityType.OTHER, show.entityType)
    }

    @Test
    fun `correcting types leaves a real track alone`() {
        val track = SearchHistoryEntity(
            id = "vid_123",
            title = "Test Song",
            subtitle = "Test Artist",
            artworkUrl = null,
            entityType = EntityType.TRACK,
        )

        assertEquals(track, track.withCorrectedType())
    }
}
