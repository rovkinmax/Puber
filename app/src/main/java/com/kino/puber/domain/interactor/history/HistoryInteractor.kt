package com.kino.puber.domain.interactor.history

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.ItemDetailsRepository

internal class HistoryInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
) {
    suspend fun getPage(page: Int): PaginatedResponse<History> {
        return api.getHistoryData(page).getOrThrow()
    }

    suspend fun clearExactMediaHistory(mediaId: Int, itemId: Int) {
        api.clearExactMediaHistory(mediaId).getOrThrow()
        itemDetailsRepository.invalidate(itemId)
    }

    fun invalidateItemDetails(itemId: Int) {
        itemDetailsRepository.invalidate(itemId)
    }
}

internal class HistoryTraversal(
    seedItems: List<History> = emptyList(),
) {
    private val seenRowKeys: MutableSet<HistoryRowKey> =
        seedItems.mapNotNullTo(mutableSetOf(), History::rowKeyOrNull)

    fun filterFirstOccurrences(items: List<History>): List<History> {
        return items.filter { history ->
            history.rowKeyOrNull()
                ?.let(seenRowKeys::add)
                ?: run {
                    log("Skipping a history row without supported media identity")
                    false
                }
        }
    }
}

internal sealed interface HistorySemanticKey {
    data class Movie(
        val itemId: Int,
        val videoNumber: Int,
    ) : HistorySemanticKey

    data class Episode(
        val itemId: Int,
        val seasonNumber: Int,
        val episodeNumber: Int,
    ) : HistorySemanticKey
}

internal sealed interface HistoryRowKey {
    data class Media(
        val semanticKey: HistorySemanticKey,
    ) : HistoryRowKey

    data class DeletionMedia(
        val mediaId: Int,
    ) : HistoryRowKey
}

internal fun History.semanticKeyOrNull(): HistorySemanticKey? {
    val media = video?.takeIf { it.id > 0 }
    if (media == null || item.id <= 0 || item.type == ItemType.UNKNOWN_VALUE) {
        return null
    }

    return if (item.type.isSeriesLike()) {
        val seasonNumber = season?.takeIf { it > 0 }
        val episodeNumber = media.number?.takeIf { it > 0 }
        if (seasonNumber != null && episodeNumber != null) {
            HistorySemanticKey.Episode(
                itemId = item.id,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
        } else {
            null
        }
    } else {
        media.number
            ?.takeIf { it > 0 }
            ?.let { videoNumber ->
                HistorySemanticKey.Movie(
                    itemId = item.id,
                    videoNumber = videoNumber,
                )
            }
    }
}

internal fun History.rowKeyOrNull(): HistoryRowKey? {
    return semanticKeyOrNull()?.let(HistoryRowKey::Media)
        ?: video
            ?.id
            ?.takeIf {
                it > 0 &&
                    item.id > 0 &&
                    item.type.isSeriesLike()
            }
            ?.let(HistoryRowKey::DeletionMedia)
}
