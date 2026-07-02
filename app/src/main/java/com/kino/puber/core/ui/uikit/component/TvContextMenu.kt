package com.kino.puber.core.ui.uikit.component

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.TvContextMenuAction
import com.kino.puber.core.ui.uikit.model.UIAction
import kotlinx.coroutines.delay

private const val ACTION_WATCH = "watch"
private const val ACTION_DETAILS = "details"
private const val ACTION_ADD_TO_SAVED = "add_to_saved"
private const val ACTION_REMOVE_FROM_SAVED = "remove_from_saved"
private const val ACTION_REFRESH_TAB = "refresh_tab"
private const val ACTION_MARK_EPISODE_WATCHED = "mark_episode_watched"
private const val ACTION_MARK_EPISODE_UNWATCHED = "mark_episode_unwatched"
private const val ACTION_MARK_SEASON_WATCHED = "mark_season_watched"
private const val ACTION_MARK_SEASON_UNWATCHED = "mark_season_unwatched"
private const val CONTEXT_MENU_FOCUS_DELAY_MS = 100L
private const val CONTEXT_MENU_FOCUS_ATTEMPTS = 4
private const val CONTEXT_MENU_FOCUS_RETRY_DELAY_MS = 50L
private const val LONG_SELECT_REPEAT_THRESHOLD = 1

private val ContextMenuWidth = 520.dp

@Composable
internal fun TvContextMenuDialog(
    title: String,
    actions: List<TvContextMenuAction>,
    onAction: (TvContextMenuAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    val actionFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val initialFocusActionIndex = remember(actions) {
        actions.indexOfFirst { it.enabled }
    }

    LaunchedEffect(actions) {
        delay(CONTEXT_MENU_FOCUS_DELAY_MS)
        repeat(CONTEXT_MENU_FOCUS_ATTEMPTS) {
            val focused = runCatching {
                if (initialFocusActionIndex >= 0) {
                    actionFocusRequester.requestFocus()
                } else {
                    closeFocusRequester.requestFocus()
                }
            }.getOrDefault(false)
            if (focused) {
                return@LaunchedEffect
            }
            delay(CONTEXT_MENU_FOCUS_RETRY_DELAY_MS)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Card(
            modifier = modifier.width(ContextMenuWidth),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                actions.forEachIndexed { index, action ->
                    TvSafeButton(
                        text = action.title,
                        onClick = { onAction(action) },
                        enabled = action.enabled,
                        primary = index == 0,
                        modifier = if (index == initialFocusActionIndex) {
                            Modifier.focusRequester(actionFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
                TvSafeButton(
                    text = stringResource(R.string.context_menu_close),
                    onClick = onDismiss,
                    modifier = if (initialFocusActionIndex < 0) {
                        Modifier.focusRequester(closeFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
internal fun VideoItemContextMenuDialog(
    item: VideoItemUIState?,
    onDismiss: () -> Unit,
    onAction: (UIAction) -> Unit,
) {
    if (item == null) return
    val savedAction = TvContextMenuAction(
        id = if (item.isSaved) ACTION_REMOVE_FROM_SAVED else ACTION_ADD_TO_SAVED,
        title = stringResource(
            when {
                item.isSeriesLike && item.isSaved -> R.string.context_menu_remove_from_watchlist
                item.isSeriesLike -> R.string.context_menu_add_to_watchlist
                item.isSaved -> R.string.context_menu_remove_from_bookmarks
                else -> R.string.context_menu_add_to_bookmarks
            }
        ),
    )
    TvContextMenuDialog(
        title = item.title,
        actions = listOf(
            TvContextMenuAction(
                id = ACTION_WATCH,
                title = stringResource(
                    if (item.isSeriesLike) {
                        R.string.context_menu_watch_series
                    } else {
                        R.string.context_menu_watch_movie
                    }
                ),
            ),
            TvContextMenuAction(
                id = ACTION_DETAILS,
                title = stringResource(R.string.context_menu_details),
            ),
            savedAction,
        ),
        onAction = { action ->
            onDismiss()
            when (action.id) {
                ACTION_WATCH -> onAction(CommonAction.ItemPlayed(item))
                ACTION_DETAILS -> onAction(CommonAction.ItemSelected(item))
                ACTION_ADD_TO_SAVED -> onAction(CommonAction.ItemSavedChanged(item, true))
                ACTION_REMOVE_FROM_SAVED -> onAction(CommonAction.ItemSavedChanged(item, false))
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun EpisodeContextMenuDialog(
    episode: VideoItemUIState?,
    onDismiss: () -> Unit,
    onPlay: (VideoItemUIState) -> Unit,
    onMarkEpisodeWatched: (VideoItemUIState, Boolean) -> Unit,
    onMarkSeasonWatched: (VideoItemUIState, Boolean) -> Unit,
) {
    if (episode == null) return
    val isWatched = episode.isWatched
    val isSeasonWatched = episode.isSeasonWatched ?: isWatched
    TvContextMenuDialog(
        title = episode.title,
        actions = listOf(
            TvContextMenuAction(
                id = ACTION_WATCH,
                title = stringResource(R.string.context_menu_watch_episode),
            ),
            TvContextMenuAction(
                id = if (isWatched) ACTION_MARK_EPISODE_UNWATCHED else ACTION_MARK_EPISODE_WATCHED,
                title = stringResource(
                    if (isWatched) {
                        R.string.context_menu_unmark_episode_watched
                    } else {
                        R.string.context_menu_mark_episode_watched
                    }
                ),
            ),
            TvContextMenuAction(
                id = if (isSeasonWatched) ACTION_MARK_SEASON_UNWATCHED else ACTION_MARK_SEASON_WATCHED,
                title = stringResource(
                    if (isSeasonWatched) {
                        R.string.context_menu_unmark_season_watched
                    } else {
                        R.string.context_menu_mark_season_watched
                    }
                ),
                enabled = episode.seasonNumber != null,
            ),
        ),
        onAction = { action ->
            onDismiss()
            when (action.id) {
                ACTION_WATCH -> onPlay(episode)
                ACTION_MARK_EPISODE_WATCHED -> onMarkEpisodeWatched(episode, true)
                ACTION_MARK_EPISODE_UNWATCHED -> onMarkEpisodeWatched(episode, false)
                ACTION_MARK_SEASON_WATCHED -> onMarkSeasonWatched(episode, true)
                ACTION_MARK_SEASON_UNWATCHED -> onMarkSeasonWatched(episode, false)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun TopTabContextMenuDialog(
    title: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (title == null) return
    TvContextMenuDialog(
        title = title,
        actions = listOf(
            TvContextMenuAction(
                id = ACTION_REFRESH_TAB,
                title = stringResource(R.string.context_menu_refresh_tab),
            ),
        ),
        onAction = { action ->
            onDismiss()
            when (action.id) {
                ACTION_REFRESH_TAB -> onRefresh()
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun Modifier.onTvContextMenuKey(
    enabled: Boolean = true,
    onOpen: () -> Unit,
): Modifier {
    var suppressSelectUp by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        if (!enabled) return@onPreviewKeyEvent false

        val native = event.nativeKeyEvent
        when {
            event.type == KeyEventType.KeyDown && native.keyCode.isContextMenuKeyCode() -> {
                onOpen()
                true
            }

            event.type == KeyEventType.KeyDown &&
                event.key.isSelectKey() &&
                native.repeatCount >= LONG_SELECT_REPEAT_THRESHOLD -> {
                suppressSelectUp = true
                true
            }

            event.type == KeyEventType.KeyUp && event.key.isSelectKey() && suppressSelectUp -> {
                suppressSelectUp = false
                onOpen()
                true
            }

            else -> false
        }
    }
}

private fun Int.isContextMenuKeyCode(): Boolean {
    return this == KeyEvent.KEYCODE_MENU ||
        this == KeyEvent.KEYCODE_SETTINGS ||
        this == KeyEvent.KEYCODE_TV_CONTENTS_MENU ||
        this == KeyEvent.KEYCODE_ASSIST
}

private fun Key.isSelectKey(): Boolean = this == Key.DirectionCenter || this == Key.Enter
