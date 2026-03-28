package com.kino.puber.ui.feature.details.component

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.CaretDown
import com.adamglin.phosphoricons.fill.BookmarkSimple
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.player.component.EpisodesPanel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun DetailsScreenContent(
    state: DetailsScreenState,
    onAction: (UIAction) -> Unit,
) {
    when (state) {
        is DetailsScreenState.Loading -> DetailsContentSkeleton()
        is DetailsScreenState.Error -> FullScreenError(
            error = state.message,
            onClick = { onAction(CommonAction.RetryClicked) },
        )

        is DetailsScreenState.Content -> {
            val seasonsPanelFocusRequester = remember { FocusRequester() }
            LaunchedEffect(state.seasonsPanelVisible) {
                if (state.seasonsPanelVisible) {
                    delay(150)
                    try {
                        seasonsPanelFocusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                DetailsContentBody(
                    state = state,
                    onAction = onAction,
                )
                if (state.episodes != null) {
                    EpisodesPanel(
                        visible = state.seasonsPanelVisible,
                        episodes = state.episodes,
                        onEpisodeSelected = { item -> onAction(DetailsAction.EpisodeSelected(item)) },
                        onBackPressed = { onAction(DetailsAction.CloseSeasonsPanel) },
                        modifier = Modifier.focusRequester(seasonsPanelFocusRequester),
                    )
                }
                TrailerOverlay(
                    url = state.trailerUrl,
                )
            }
        }
    }
}

@Composable
private fun DetailsContentBody(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
) {
    DetailsMainPage(state = state, onAction = onAction, seasonsPanelVisible = state.seasonsPanelVisible)
}

@Composable
private fun DetailsMainPage(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    seasonsPanelVisible: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3F),
            state = state.details,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionButtonsRow(
            buttons = state.buttons,
            isInWatchlist = state.isInWatchlist,
            onAction = onAction,
            seasonsPanelVisible = seasonsPanelVisible,
        )

        Spacer(modifier = Modifier.weight(1F))

        ChevronIndicator()
    }
}

@Composable
private fun ActionButtonsRow(
    buttons: List<DetailsButtonUIState>,
    isInWatchlist: Boolean,
    onAction: (UIAction) -> Unit,
    seasonsPanelVisible: Boolean,
) {
    val focusRequester = rememberFocusRequesterOnLaunch()
    val firstButtonFocusRequester = remember { FocusRequester() }

    // Re-request focus after navigation return (Player → Back).
    // Skip when seasons panel is visible — it owns focus in that case.
    LaunchedEffect(Unit) {
        delay(100)
        if (!seasonsPanelVisible) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester)
            .focusRestorer(firstButtonFocusRequester),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, button ->
            val buttonModifier =
                if (index == 0) Modifier.focusRequester(firstButtonFocusRequester) else Modifier

            when (button) {
                is DetailsButtonUIState.TextButton -> {
                    Button(onClick = { onAction(button.action) }, modifier = buttonModifier) {
                        Icon(
                            imageVector = button.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = button.textOverride ?: stringResource(button.textRes))
                    }
                }

                is DetailsButtonUIState.IconOnly -> {
                    IconButton(
                        onClick = { onAction(button.action) },
                        modifier = buttonModifier,
                    ) {
                        Icon(
                            imageVector = button.icon,
                            contentDescription = stringResource(button.contentDescription),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                is DetailsButtonUIState.WatchlistToggle -> {
                    IconButton(
                        onClick = { onAction(button.action) },
                        modifier = buttonModifier,
                    ) {
                        Icon(
                            imageVector = if (isInWatchlist) {
                                PhosphorIcons.Fill.BookmarkSimple
                            } else {
                                PhosphorIcons.Duotone.BookmarkSimple
                            },
                            contentDescription = stringResource(button.contentDescription),
                            modifier = Modifier.size(20.dp),
                            tint = if (isInWatchlist) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChevronIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Duotone.CaretDown,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .alpha(0.5f),
        )
    }
}

@Composable
private fun DetailsContentSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3F),
            state = VideoDetailsUIState.Loading,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
        }

        Spacer(modifier = Modifier.weight(1F))

        ChevronIndicator()
    }
}