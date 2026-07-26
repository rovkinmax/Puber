package com.kino.puber.ui.feature.history.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvContextMenuDialog
import com.kino.puber.core.ui.uikit.model.TvContextMenuAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.player.model.PlayerStartMode

private const val ACTION_PLAY = "history_play"
private const val ACTION_DETAILS = "history_details"
private const val ACTION_DELETE_EXACT_MEDIA = "history_delete_exact_media"

@Composable
internal fun HistoryContextMenu(
    item: HistoryItemUIState?,
    isDeleteExactMediaAvailable: Boolean,
    onAction: (UIAction) -> Unit,
) {
    if (item == null) return
    val playbackMenuAction = historyPlaybackMenuAction(item)
    val playbackAction = playbackMenuAction?.let { action ->
        TvContextMenuAction(
            id = ACTION_PLAY,
            title = stringResource(action.titleRes),
        )
    }

    TvContextMenuDialog(
        title = historyContextMenuTitle(item),
        supportingText = stringResource(R.string.history_context_account_disclosure),
        actions = listOfNotNull(
            playbackAction,
            TvContextMenuAction(
                id = ACTION_DETAILS,
                title = stringResource(R.string.context_menu_details),
            ),
            TvContextMenuAction(
                id = ACTION_DELETE_EXACT_MEDIA,
                title = stringResource(R.string.history_context_delete_exact_media),
                enabled = isDeleteExactMediaAvailable,
            ),
        ),
        onAction = { action ->
            when (action.id) {
                ACTION_PLAY -> playbackMenuAction?.let { playback ->
                    onAction(HistoryAction.Play(item, playback.startMode))
                }
                ACTION_DETAILS -> onAction(HistoryAction.OpenDetails(item))
                ACTION_DELETE_EXACT_MEDIA -> onAction(HistoryAction.DeleteExactMedia(item))
            }
        },
        onDismiss = { onAction(HistoryAction.DismissContextMenu) },
    )
}

internal data class HistoryPlaybackMenuAction(
    @param:StringRes val titleRes: Int,
    val startMode: PlayerStartMode,
)

internal fun historyPlaybackMenuAction(item: HistoryItemUIState): HistoryPlaybackMenuAction? {
    if (item.playbackTarget == HistoryPlaybackTarget.Details) return null
    return if (item.progressPercent?.let { it > 0f } == true && !item.isWatched) {
        HistoryPlaybackMenuAction(
            titleRes = R.string.history_context_continue,
            startMode = PlayerStartMode.ResumeIfAvailable,
        )
    } else {
        HistoryPlaybackMenuAction(
            titleRes = R.string.history_context_play,
            startMode = PlayerStartMode.StartFromBeginning,
        )
    }
}

@Composable
private fun historyContextMenuTitle(item: HistoryItemUIState): String {
    val seasonNumber = item.seasonNumber
    val episodeNumber = item.episodeNumber
    return if (seasonNumber != null && episodeNumber != null) {
        stringResource(
            R.string.history_context_episode_title,
            item.card.title,
            seasonNumber,
            episodeNumber,
        )
    } else {
        item.card.title
    }
}
