package com.kino.puber.ui.feature.history.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import java.util.UUID

internal const val HISTORY_CARD_TEST_TAG_PREFIX = "history_card_"
private const val OPAQUE_TOKEN_ALPHABET = "abcdefghijklmnop"
private const val HEX_RADIX = 16

@Composable
internal fun HistoryCard(
    state: HistoryItemUIState,
    requestFocus: Boolean,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onContextMenu: () -> Unit,
    onFocus: () -> Unit,
    blockRightFocusExit: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember(state.rowKey) { FocusRequester() }
    val contentFocusActive = LocalContentFocusActive.current

    LaunchedEffect(requestFocus, contentFocusActive) {
        if (requestFocus && contentFocusActive) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    Box(modifier = modifier) {
        VideoItemHorizontal(
            modifier = Modifier
                .focusRequester(focusRequester)
                .then(
                    if (blockRightFocusExit) {
                        Modifier.focusProperties {
                            right = FocusRequester.Cancel
                        }
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocus()
                    }
                }
                .opaqueHistoryCardTestTag(state.rowKey),
            state = state.card.copy(
                isWatched = state.isWatched,
                showWatchedIndicator = state.isWatched || state.card.showWatchedIndicator,
            ),
            onClick = { if (!isDeleting) onClick() },
            onContextMenu = onContextMenu.takeUnless { isDeleting },
        )

        HistoryEpisodeMetadata(state)
        HistoryDeletingOverlay(visible = isDeleting)
    }
}

@Composable
private fun BoxScope.HistoryEpisodeMetadata(state: HistoryItemUIState) {
    val seasonNumber = state.seasonNumber ?: return
    val episodeNumber = state.episodeNumber ?: return
    Text(
        text = stringResource(
            R.string.history_episode_metadata,
            seasonNumber,
            episodeNumber,
        ),
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .background(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun BoxScope.HistoryDeletingOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Modifier.opaqueHistoryCardTestTag(rowKey: HistoryRowKey): Modifier {
    val accessibilityTestTag = rememberSaveable(rowKey) {
        newHistoryCardTestTag()
    }
    return testTag(accessibilityTestTag)
}

private fun newHistoryCardTestTag(): String {
    val opaqueToken = UUID.randomUUID()
        .toString()
        .asSequence()
        .filterNot { it == '-' }
        .map { hexCharacter ->
            OPAQUE_TOKEN_ALPHABET[hexCharacter.digitToInt(radix = HEX_RADIX)]
        }
        .joinToString(separator = "")
    return HISTORY_CARD_TEST_TAG_PREFIX + opaqueToken
}

internal fun HistoryRowKey.toHistoryUiKey(): String {
    return when (this) {
        is HistoryRowKey.Media -> when (val key = semanticKey) {
            is HistorySemanticKey.Movie -> "movie_${key.itemId}_${key.videoNumber}"
            is HistorySemanticKey.Episode ->
                "episode_${key.itemId}_${key.seasonNumber}_${key.episodeNumber}"
        }
        is HistoryRowKey.DeletionMedia -> "deletion_media_$mediaId"
    }
}
