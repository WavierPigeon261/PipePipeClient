package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.image.ImageStrategy
import java.time.OffsetDateTime

data class StreamHistoryEntry(
    @Embedded
    val streamEntity: StreamEntity,

    @ColumnInfo(name = StreamHistoryEntity.JOIN_STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_ACCESS_DATE)
    val accessDate: OffsetDateTime,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_REPEAT_COUNT)
    val repeatCount: Long,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_CHANNEL_ID)
    val channelId: String?,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_CHANNEL_URL)
    val channelUrl: String?,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_TAGS)
    val tagsJson: String?,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_LAST_POSITION_MS)
    val lastPositionMs: Long,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_COMPLETION_RATIO)
    val completionRatio: Double
) {

    fun toStreamHistoryEntity(): StreamHistoryEntity {
        return StreamHistoryEntity(
            streamId, accessDate, repeatCount, channelId, channelUrl,
            tagsJson, lastPositionMs, completionRatio
        )
    }

    fun hasEqualValues(other: StreamHistoryEntry): Boolean {
        return this.streamEntity.uid == other.streamEntity.uid && streamId == other.streamId &&
            accessDate.isEqual(other.accessDate)
    }

    fun toStreamInfoItem(): StreamInfoItem =
        StreamInfoItem(
            streamEntity.serviceId,
            streamEntity.url,
            streamEntity.title,
            streamEntity.streamType,
        ).apply {
            duration = streamEntity.duration
            uploaderName = streamEntity.uploader
            uploaderUrl = streamEntity.uploaderUrl
            thumbnailUrl = streamEntity.thumbnailUrl
        }
}
