package org.schabi.newpipe.local.history

import org.schabi.newpipe.extractor.stream.StreamInfo

object WatchHistoryMetadata {

    @JvmStatic
    fun channelIdFrom(uploaderUrl: String): String? = channelIdFrom(uploaderUrl, null)

    @JvmStatic
    fun channelIdFrom(uploaderUrl: String?, fallback: String?): String? {
        val trimmedUrl = uploaderUrl?.trim() ?: return fallback?.takeIf { it.isNotBlank() }
        if (trimmedUrl.isBlank()) {
            return fallback?.takeIf { it.isNotBlank() }
        }

        val extracted = trimmedUrl.trimEnd('/').substringAfterLast('/').trim()
        return extracted.ifBlank { fallback?.takeIf { it.isNotBlank() } }
    }

    @JvmStatic
    fun channelIdFrom(info: StreamInfo): String? {
        return channelIdFrom(info.uploaderUrl, info.uploaderName)
    }

    @JvmStatic
    fun tagsJson(tags: List<String>?): String? {
        val normalized = tags
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.toList()
            ?: return null

        return if (normalized.isEmpty()) null else normalized.joinToString(",")
    }
}