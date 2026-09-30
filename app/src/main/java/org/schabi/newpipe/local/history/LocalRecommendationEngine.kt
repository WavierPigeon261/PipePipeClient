package org.schabi.newpipe.local.history

import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.max

class LocalRecommendationEngine(
    private val historyEntries: List<StreamHistoryEntity> = emptyList(),
    private val now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
) {
    fun getRecommendedStreams(candidates: List<StreamEntity>, limit: Int = 10): List<StreamInfoItem> {
        return rankStreams(candidates)
            .take(limit.coerceAtLeast(0))
            .map { it.toStreamInfoItem() }
    }

    fun rankStreams(candidates: List<StreamEntity>): List<StreamEntity> {
        if (candidates.isEmpty() || historyEntries.isEmpty()) {
            return emptyList()
        }

        val watchedStreamIds = historyEntries.map { it.streamUid }.toSet()
        val channelWeights = mutableMapOf<String, Double>()
        val tagWeights = mutableMapOf<String, Double>()

        historyEntries.forEach { entry ->
            val channelKey = normalizeChannelKey(entry.channelId, entry.channelUrl)
            if (channelKey != null) {
                val weight = (entry.repeatCount * recencyWeight(entry)) + (channelWeights[channelKey] ?: 0.0)
                channelWeights[channelKey] = weight
            }

            entry.tagsJson
                ?.split(',')
                ?.asSequence()
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?.forEach { tag ->
                    val tagWeight = (entry.repeatCount * recencyWeight(entry)) + (tagWeights[tag] ?: 0.0)
                    tagWeights[tag] = tagWeight
                }
        }

        val maxChannelWeight = channelWeights.values.maxOrNull() ?: 1.0
        val maxTagWeight = tagWeights.values.maxOrNull() ?: 1.0

        return candidates
            .asSequence()
            .filterNot { watchedStreamIds.contains(it.uid) }
            .map { candidate ->
                val channelKey = normalizeChannelKey(candidate.uploaderUrl, candidate.uploader)
                val channelAffinity = if (channelKey == null) 0.0 else (channelWeights[channelKey] ?: 0.0) / maxChannelWeight
                val tagAffinity = scoreTagAffinity(candidate, tagWeights) / maxTagWeight
                val completionAffinity = scoreCompletionAffinity(candidate, historyEntries)
                val recommendationScore = (channelAffinity * 0.6) + (tagAffinity * 0.25) + (completionAffinity * 0.1)
                recommendationScore to candidate
            }
            .filter { it.first > 0.0 }
            .sortedByDescending { it.first }
            .map { it.second }
            .toList()
    }

    private fun scoreTagAffinity(candidate: StreamEntity, tagWeights: Map<String, Double>): Double {
        if (tagWeights.isEmpty()) {
            return 0.0
        }

        val queryTokens = tokenize("${candidate.title} ${candidate.uploader}")
        if (queryTokens.isEmpty()) {
            return 0.0
        }

        val matchedWeight = queryTokens
            .filter { tagWeights.containsKey(it) }
            .sumOf { tagWeights[it] ?: 0.0 }

        return if (matchedWeight <= 0.0) 0.0 else matchedWeight / queryTokens.size.toDouble()
    }

    private fun scoreCompletionAffinity(candidate: StreamEntity, historyEntries: List<StreamHistoryEntity>): Double {
        val candidateChannel = normalizeChannelKey(candidate.uploaderUrl, candidate.uploader) ?: return 0.0
        val sameChannelHistory = historyEntries.filter { normalizeChannelKey(it.channelId, it.channelUrl) == candidateChannel }
        if (sameChannelHistory.isEmpty()) {
            return 0.0
        }

        val averageCompletion = sameChannelHistory.map { it.completionRatio }.average()
        val averageRecency = sameChannelHistory.map { recencyWeight(it) }.average()
        return (averageCompletion * 0.7) + (averageRecency * 0.3)
    }

    private fun normalizeChannelKey(channelId: String?, channelUrl: String?): String? {
        val channel = channelId?.trim()?.ifBlank { null } ?: channelUrl?.trim()?.ifBlank { null }
        if (channel == null) {
            return null
        }

        val derived = WatchHistoryMetadata.channelIdFrom(channel, null)
        return derived?.lowercase()?.trim()
    }

    private fun recencyWeight(entry: StreamHistoryEntity): Double {
        val minutesAgo = Duration.between(entry.accessDate, now).toMinutes().toDouble()
        val ageDays = max(0.0, minutesAgo / (24.0 * 60.0))
        return exp(-(ageDays / 21.0))
    }

    private fun tokenize(value: String): Set<String> {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .toSet()
    }
}
