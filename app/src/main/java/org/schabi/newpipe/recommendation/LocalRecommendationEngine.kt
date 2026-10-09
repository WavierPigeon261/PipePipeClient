package org.schabi.newpipe.recommendation

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.WatchHistoryMetadata
import org.schabi.newpipe.util.ExtractorHelper
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.max

class LocalRecommendationEngine(
    private val historyEntries: List<StreamHistoryEntity> = emptyList(),
    private val now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    private val historyDAO: StreamHistoryDAO? = null
) {
    constructor(
        historyDAO: StreamHistoryDAO,
        now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
    ) : this(emptyList(), now, historyDAO)

    fun getRecommendedStreams(candidates: List<StreamEntity>, limit: Int = 10): List<StreamInfoItem> =
        rankStreams(candidates).take(limit.coerceAtLeast(0)).map { it.toStreamInfoItem() }

    /**
     * Fetches related items for a small number of recent, high-affinity local watch-history seeds.
     * Extractor requests are ordinary service requests; no Google Play Services or tracking API is
     * involved. Errors are intentionally propagated so the feed can report them to the user.
     */
    fun getRecommendedStreams(): Single<List<StreamInfoItem>> {
        val dao = historyDAO ?: return Single.error(
            IllegalStateException("A history DAO is required to fetch related recommendations")
        )
        val since = now.minusDays(HISTORY_WINDOW_DAYS)

        return Single.fromCallable { dao.getRecommendationHistory(since) }
            .flatMap { recentHistory ->
                val seeds = selectSeeds(recentHistory)
                Observable.fromIterable(seeds)
                    .concatMapSingle { entry ->
                        ExtractorHelper.getStreamInfo(
                            entry.streamEntity.serviceId,
                            entry.streamEntity.url,
                            false
                        )
                            .map { it.relatedItems.filterIsInstance<StreamInfoItem>() }
                    }
                    .flatMapIterable { it }
                    .filter { item ->
                        item.url.isNotBlank() &&
                            recentHistory.none {
                                it.streamEntity.serviceId == item.serviceId &&
                                    it.streamEntity.url == item.url
                            }
                    }
                    .distinct { it.serviceId.toString() + "\u0000" + it.url }
                    .toList()
                    .map { relatedItems ->
                        val recentEntries = recentHistory.map { it.toStreamHistoryEntity() }
                        LocalRecommendationEngine(recentEntries, now)
                            .rankInfoItems(relatedItems)
                            .take(RESULT_LIMIT)
                    }
            }
    }

    fun rankStreams(candidates: List<StreamEntity>): List<StreamEntity> {
        if (candidates.isEmpty() || historyEntries.isEmpty()) {
            return emptyList()
        }

        val history = historyEntries
            .filter { it.accessDate >= now.minusDays(HISTORY_WINDOW_DAYS) }
            .sortedByDescending { it.accessDate }
        val watchedIds = history.mapTo(mutableSetOf()) { it.streamUid }
        val channelWeights = channelWeights(history)
        val tagWeights = tagWeights(history.take(TAG_HISTORY_LIMIT))
        val maximumChannelWeight = channelWeights.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val maximumTagWeight = tagWeights.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

        return candidates.asSequence()
            .filterNot { it.uid in watchedIds }
            .map { candidate ->
                val channelScore = channelKeys(candidate.uploaderUrl, candidate.uploader)
                    .maxOfOrNull { channelWeights[it] ?: 0.0 } ?: 0.0
                val tagScore = scoreTagAffinity(candidate.title, candidate.uploader, tagWeights)
                val score = (channelScore / maximumChannelWeight) * CHANNEL_WEIGHT +
                    (tagScore / maximumTagWeight) * TAG_WEIGHT
                score to candidate
            }
            .filter { it.first > 0.0 }
            .sortedByDescending { it.first }
            .map { it.second }
            .toList()
    }

    private fun rankInfoItems(items: List<StreamInfoItem>): List<StreamInfoItem> {
        if (items.isEmpty() || historyEntries.isEmpty()) {
            return items
        }
        val entities = items.map(::StreamEntity)
        val rankedUrls = rankStreams(entities).map { it.serviceId.toString() + "\u0000" + it.url }
        val byUrl = items.associateBy { it.serviceId.toString() + "\u0000" + it.url }
        return rankedUrls.mapNotNull(byUrl::get)
    }

    private fun selectSeeds(history: List<StreamHistoryEntry>): List<StreamHistoryEntry> {
        if (history.isEmpty()) {
            return emptyList()
        }
        val weightedHistory = history.map { it.toStreamHistoryEntity() }
        val channels = channelWeights(weightedHistory)
        val tags = tagWeights(weightedHistory.take(TAG_HISTORY_LIMIT))
        val maxChannel = channels.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val maxTag = tags.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

        return history.asSequence()
            .map { entry ->
                val channelScore = channelKeys(entry.channelId, entry.channelUrl)
                    .maxOfOrNull { channels[it] ?: 0.0 } ?: 0.0
                val tagTokens = parseTags(entry.tagsJson).flatMap(::tokenize).distinct()
                val tagScore = tagTokens.sumOf { tags[it] ?: 0.0 }
                val normalizedTagScore = if (tags.isEmpty() || tagTokens.isEmpty()) {
                    0.0
                } else {
                    tagScore / tagTokens.size / maxTag
                }
                entry to ((channelScore / maxChannel) * CHANNEL_WEIGHT +
                    normalizedTagScore * TAG_WEIGHT)
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.streamId }
            .take(SEED_LIMIT)
            .toList()
    }

    private fun channelWeights(history: List<StreamHistoryEntity>): Map<String, Double> =
        history.flatMap { entry ->
            val keys = channelKeys(entry.channelId, entry.channelUrl)
            if (keys.isEmpty()) {
                emptyList()
            } else {
                keys.map { key ->
                    key to ((entry.repeatCount.coerceAtLeast(1L)) * recencyWeight(entry))
                }
            }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, weights) -> weights.sum() }

    private fun tagWeights(history: List<StreamHistoryEntity>): Map<String, Double> {
        val weights = mutableMapOf<String, Double>()
        history.take(TAG_HISTORY_LIMIT).forEach { entry ->
            parseTags(entry.tagsJson).flatMap(::tokenize).distinct().forEach { token ->
                weights[token] = (weights[token] ?: 0.0) +
                    entry.repeatCount.coerceAtLeast(1L) * recencyWeight(entry)
            }
        }
        return weights
    }

    private fun scoreTagAffinity(
        title: String,
        uploader: String,
        tagWeights: Map<String, Double>
    ): Double {
        val tokens = tokenize("$title $uploader")
        if (tokens.isEmpty()) {
            return 0.0
        }
        return tokens.sumOf { tagWeights[it] ?: 0.0 } / tokens.size
    }

    private fun normalizeChannelKey(channelId: String?, channelUrl: String?): String? {
        val raw = channelId?.trim()?.takeIf { it.isNotEmpty() }
            ?: channelUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return WatchHistoryMetadata.channelIdFrom(raw, null)?.lowercase()
    }

    private fun channelKeys(primary: String?, secondary: String?): Set<String> =
        listOfNotNull(
            normalizeChannelKey(primary, null),
            normalizeChannelKey(secondary, null)
        ).toSet()

    private fun recencyWeight(entry: StreamHistoryEntity): Double {
        val ageDays = max(0.0, Duration.between(entry.accessDate, now).toSeconds() / SECONDS_PER_DAY)
        return exp(-LN_2 * ageDays / HALF_LIFE_DAYS)
    }

    private fun parseTags(tags: String?): List<String> {
        if (tags.isNullOrBlank()) {
            return emptyList()
        }
        val trimmed = tags.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return JSON_STRING_REGEX.findAll(trimmed)
                .map { unescapeJsonString(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
        }
        return trimmed.split(',').map(String::trim).filter(String::isNotEmpty)
    }

    private fun tokenize(value: String): Set<String> = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 2 }
        .toSet()

    private fun unescapeJsonString(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    companion object {
        private const val HISTORY_WINDOW_DAYS = 30L
        private const val TAG_HISTORY_LIMIT = 20
        private const val SEED_LIMIT = 3
        private const val RESULT_LIMIT = 20
        private const val CHANNEL_WEIGHT = 0.65
        private const val TAG_WEIGHT = 0.35
        private const val HALF_LIFE_DAYS = 7.0
        private const val SECONDS_PER_DAY = 86_400.0
        private const val LN_2 = 0.6931471805599453
        private val JSON_STRING_REGEX = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
    }
}
