package com.kino.puber.ui.feature.history.component.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.history.model.HistoryViewState

private val movieKey = HistorySemanticKey.Movie(itemId = 101, videoNumber = 2)
private val episodeKey = HistorySemanticKey.Episode(
    itemId = 202,
    seasonNumber = 2,
    episodeNumber = 5,
)
private val completedKey = HistorySemanticKey.Movie(itemId = 303, videoNumber = 1)
private val longTitleKey = HistorySemanticKey.Movie(itemId = 505, videoNumber = 1)

internal class HistoryScreenPreviewProvider : PreviewParameterProvider<HistoryViewState> {
    override val values: Sequence<HistoryViewState> = sequenceOf(
        HistoryViewState.Loading,
        contentState(),
        contextMenuState(),
        deletionPendingState(),
        HistoryViewState.Empty,
        HistoryViewState.Error("Не удалось загрузить историю просмотров"),
    )
}

private fun contextMenuState(): HistoryViewState.Content {
    val rowKey = HistoryRowKey.Media(movieKey)
    return contentState().copy(
        isLoadingMore = false,
        openMenuKey = rowKey,
        focusKey = rowKey,
    )
}

private fun deletionPendingState(): HistoryViewState.Content {
    val rowKey = HistoryRowKey.Media(movieKey)
    return contentState().copy(
        isLoadingMore = false,
        deletingKeys = setOf(rowKey),
        focusKey = rowKey,
    )
}

private fun contentState(): HistoryViewState.Content {
    return HistoryViewState.Content(
        items = listOf(
            moviePreviewItem(),
            episodePreviewItem(),
            completedPreviewItem(),
            incompleteSeriesPreviewItem(),
            longTitlePreviewItem(),
        ),
        isLoadingMore = true,
        focusKey = HistoryRowKey.Media(movieKey),
    )
}

private fun moviePreviewItem() = previewItem(
    itemId = 101,
    deletionMediaId = 1001,
    rowKey = HistoryRowKey.Media(movieKey),
    semanticKey = movieKey,
    title = "Дюна: Часть вторая",
    progressPercent = 0.73f,
    playbackTarget = HistoryPlaybackTarget.Movie(videoNumber = 2),
)

private fun episodePreviewItem() = previewItem(
    itemId = 202,
    deletionMediaId = 2001,
    rowKey = HistoryRowKey.Media(episodeKey),
    semanticKey = episodeKey,
    title = "Разделение",
    progressPercent = 0.31f,
    seasonNumber = 2,
    episodeNumber = 5,
    playbackTarget = HistoryPlaybackTarget.Episode(
        seasonNumber = 2,
        episodeNumber = 5,
    ),
)

private fun completedPreviewItem() = previewItem(
    itemId = 303,
    deletionMediaId = 3001,
    rowKey = HistoryRowKey.Media(completedKey),
    semanticKey = completedKey,
    title = "Интерстеллар",
    isWatched = true,
    playbackTarget = HistoryPlaybackTarget.Movie(videoNumber = 1),
)

private fun incompleteSeriesPreviewItem() = previewItem(
    itemId = 404,
    deletionMediaId = 4001,
    rowKey = HistoryRowKey.DeletionMedia(mediaId = 4001),
    semanticKey = null,
    title = "Сериал с неполными координатами эпизода",
    progressPercent = 0.18f,
    playbackTarget = HistoryPlaybackTarget.Details,
)

private fun longTitlePreviewItem() = previewItem(
    itemId = 505,
    deletionMediaId = 5001,
    rowKey = HistoryRowKey.Media(longTitleKey),
    semanticKey = longTitleKey,
    title = "Очень длинное название фильма без доступного изображения для проверки карточки",
    progressPercent = 0.52f,
    playbackTarget = HistoryPlaybackTarget.Movie(videoNumber = 1),
)

private fun previewItem(
    itemId: Int,
    deletionMediaId: Int,
    rowKey: HistoryRowKey,
    semanticKey: HistorySemanticKey?,
    title: String,
    progressPercent: Float? = null,
    isWatched: Boolean = false,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    playbackTarget: HistoryPlaybackTarget,
): HistoryItemUIState {
    return HistoryItemUIState(
        itemId = itemId,
        deletionMediaId = deletionMediaId,
        rowKey = rowKey,
        semanticKey = semanticKey,
        videoNumber = (playbackTarget as? HistoryPlaybackTarget.Movie)?.videoNumber,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        progressPercent = progressPercent,
        isWatched = isWatched,
        lastViewedAt = "2099-07-23T12:00:00Z",
        playbackTarget = playbackTarget,
        card = VideoItemUIState(
            id = itemId,
            title = title,
            imageUrl = "",
            bigImageUrl = "",
            wideImageUrl = "",
            showTitle = true,
            progressPercent = progressPercent,
            isWatched = isWatched,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        ),
    )
}
