package org.schabi.newpipe.local.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchHistoryMetadataTest {
    @Test
    fun `channelIdFrom extracts final path segment from uploader url`() {
        val result = WatchHistoryMetadata.channelIdFrom("https://www.youtube.com/channel/abc123")
        assertEquals("abc123", result)
    }

    @Test
    fun `tagsJson compactly flattens and deduplicates tags`() {
        val result = WatchHistoryMetadata.tagsJson(listOf("kotlin", "android", "kotlin", "", "  "))
        assertEquals("kotlin,android", result)
    }

    @Test
    fun `tagsJson returns null for empty or blank tags`() {
        assertNull(WatchHistoryMetadata.tagsJson(emptyList()))
        assertNull(WatchHistoryMetadata.tagsJson(listOf("   ")))
    }
}
