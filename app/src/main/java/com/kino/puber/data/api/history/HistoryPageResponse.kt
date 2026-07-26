package com.kino.puber.data.api.history

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Posters
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class HistoryPageResponse(
    @SerialName("history")
    val history: List<HistoryEntryResponse>,
    val pagination: Pagination,
) {
    fun toModel(): PaginatedResponse<History> {
        return PaginatedResponse(
            items = history
                .asSequence()
                .filterNot(HistoryEntryResponse::deleted)
                .map(HistoryEntryResponse::toModel)
                .toList(),
            pagination = pagination,
        )
    }
}

@Serializable
internal data class HistoryEntryResponse(
    val counter: Int,
    @SerialName("first_seen")
    val firstSeen: Long,
    val item: HistoryItemResponse,
    @SerialName("last_seen")
    val lastSeen: Long,
    val media: HistoryMediaResponse,
    val time: Int,
    val deleted: Boolean = false,
) {
    fun toModel(): History {
        val duration = media.duration.takeIf { it > 0 }
        val watched = duration?.let { time >= it } == true
        return History(
            item = item.toModel(),
            video = Video(
                id = media.id,
                number = media.number.takeIf { it > 0 },
                duration = duration,
                watched = watched.toStatus(),
                watching = WatchingInfo(
                    time = time.coerceAtLeast(0),
                    duration = duration ?: 0,
                    status = watched.toStatus(),
                    updatedAt = lastSeen.toString(),
                ),
            ),
            season = media.seasonNumber.takeIf { it > 0 },
            time = time,
            updated = lastSeen.toString(),
        )
    }
}

@Serializable
internal data class HistoryItemResponse(
    val id: Int,
    val title: String,
    val type: ItemType,
    val posters: Posters? = null,
) {
    fun toModel(): Item {
        return Item(
            id = id,
            title = title,
            type = type,
            posters = posters,
        )
    }
}

@Serializable
internal data class HistoryMediaResponse(
    val id: Int,
    val number: Int,
    @SerialName("snumber")
    val seasonNumber: Int,
    val duration: Int,
)

private fun Boolean.toStatus(): Int = if (this) 1 else 0
