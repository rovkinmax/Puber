package com.kino.puber.ui.feature.history.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey

@Immutable
internal data class HistoryItemUIState(
    val itemId: Int,
    val deletionMediaId: Int,
    val rowKey: HistoryRowKey,
    val semanticKey: HistorySemanticKey?,
    val videoNumber: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val progressPercent: Float?,
    val isWatched: Boolean,
    val lastViewedAt: String?,
    val playbackTarget: HistoryPlaybackTarget,
    val card: VideoItemUIState,
)

@Immutable
internal sealed interface HistoryPlaybackTarget {
    data class Movie(
        val videoNumber: Int,
    ) : HistoryPlaybackTarget

    data class Episode(
        val seasonNumber: Int,
        val episodeNumber: Int,
    ) : HistoryPlaybackTarget

    data object Details : HistoryPlaybackTarget
}
