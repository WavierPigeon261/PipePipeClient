package org.schabi.newpipe.local.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.time.OffsetDateTime

class LocalRecommendationEngineTest {
    @Test
    fun `recommends streams from frequently watched channels first`() {
        val now = OffsetDateTime.parse("2026-09-30T12:00:00Z")
        val history = listOf(
            StreamHistoryEntity(1, now.minusDays(2), 5, "channel-xyz", "https://example.com/channel/xyz", "android,kotlin", 130_000, 0.8),
            StreamHistoryEntity(2, now.minusDays(7), 2, "channel-other", "https://example.com/channel/other", "music", 80_000, 0.4),
        )

        val engine = LocalRecommendationEngine(history, now)
        val candidates = listOf(
            StreamEntity(uid = 11, serviceId = 0, url = "https://example.com/watch/related", title = "Build Android with Kotlin", streamType = org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM, duration = 240, uploader = "Channel XYZ", uploaderUrl = "https://example.com/channel/xyz", thumbnailUrl = "https://example.com/thumb/a.jpg"),
            StreamEntity(uid = 12, serviceId = 0, url = "https://example.com/watch/other", title = "Cool songs playlist", streamType = org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM, duration = 300, uploader = "Other Channel", uploaderUrl = "https://example.com/channel/other", thumbnailUrl = "https://example.com/thumb/b.jpg"),
        )

        val recommended = engine.getRecommendedStreams(candidates, 10)
        assertEquals("https://example.com/watch/related", recommended.first().url)
    }

    @Test
    fun `ignores already watched streams`() {
        val now = OffsetDateTime.parse("2026-09-30T12:00:00Z")
        val history = listOf(
            StreamHistoryEntity(99, now.minusDays(1), 4, "channel-xyz", "https://example.com/channel/xyz", "android", 180_000, 0.9),
        )

        val engine = LocalRecommendationEngine(history, now)
        val candidates = listOf(
            StreamEntity(uid = 99, serviceId = 0, url = "https://example.com/watch/seen", title = "Already watched", streamType = org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM, duration = 200, uploader = "Channel XYZ", uploaderUrl = "https://example.com/channel/xyz", thumbnailUrl = "https://example.com/thumb/c.jpg"),
            StreamEntity(uid = 100, serviceId = 0, url = "https://example.com/watch/new", title = "New watch candidate", streamType = org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM, duration = 220, uploader = "Channel XYZ", uploaderUrl = "https://example.com/channel/xyz", thumbnailUrl = "https://example.com/thumb/d.jpg"),
        )

        val recommended = engine.getRecommendedStreams(candidates, 10)
        assertEquals(1, recommended.size)
        assertEquals("https://example.com/watch/new", recommended.first().url)
        assertTrue(recommended.none { it.url == "https://example.com/watch/seen" })
    }
}
