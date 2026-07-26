package com.kino.puber.ui.feature.history.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.data.api.models.History
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.domain.interactor.history.rowKeyOrNull
import com.kino.puber.domain.interactor.history.semanticKeyOrNull

internal class HistoryUIMapper(
    private val videoItemUIMapper: VideoItemUIMapper,
) {
    fun map(items: List<History>): List<HistoryItemUIState> {
        return items.mapNotNull(::map)
    }

    fun map(history: History): HistoryItemUIState? {
        val media = history.video ?: return null
        val rowKey = history.rowKeyOrNull() ?: return null
        val semanticKey = history.semanticKeyOrNull()
        val playbackTarget = semanticKey?.toPlaybackTarget() ?: HistoryPlaybackTarget.Details
        val progressPercent = media.watching?.let { watching ->
            watching.duration
                .takeIf { it > 0 }
                ?.let { duration -> watching.time.toFloat() / duration.toFloat() }
        }
        val isWatched = media.watched?.let { it == WATCHED_STATUS }
            ?: (media.watching?.status == WATCHED_STATUS)
        val seasonNumber = (semanticKey as? HistorySemanticKey.Episode)?.seasonNumber
        val episodeNumber = (semanticKey as? HistorySemanticKey.Episode)?.episodeNumber
        val videoNumber = (semanticKey as? HistorySemanticKey.Movie)?.videoNumber
        val sharedCard = videoItemUIMapper.mapShortItem(history.item)
        val card = sharedCard.copy(
            unwatchedCount = null,
            ratings = emptyList(),
            progressPercent = progressPercent,
            isWatched = isWatched,
            showWatchedIndicator = isWatched || sharedCard.showWatchedIndicator,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )

        return HistoryItemUIState(
            itemId = history.item.id,
            deletionMediaId = media.id,
            rowKey = rowKey,
            semanticKey = semanticKey,
            videoNumber = videoNumber,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            progressPercent = progressPercent,
            isWatched = isWatched,
            lastViewedAt = history.updated,
            playbackTarget = playbackTarget,
            card = card,
        )
    }

    private fun HistorySemanticKey.toPlaybackTarget(): HistoryPlaybackTarget {
        return when (this) {
            is HistorySemanticKey.Movie -> HistoryPlaybackTarget.Movie(videoNumber)
            is HistorySemanticKey.Episode -> HistoryPlaybackTarget.Episode(
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
        }
    }

    private companion object {
        const val WATCHED_STATUS = 1
    }
}
