package com.kino.puber.ui.feature.episodeschedule.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenState
import kotlinx.coroutines.delay

@Composable
internal fun EpisodeScheduleScreenContent(
    state: EpisodeScheduleScreenState,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            EpisodeScheduleScreenState.Loading -> FullScreenProgressIndicator()
            is EpisodeScheduleScreenState.Content -> ScheduleContent(
                state = state,
            )
            is EpisodeScheduleScreenState.Empty -> ScheduleEmptyContent(
                reason = state.reason,
            )
            is EpisodeScheduleScreenState.Error -> ScheduleErrorContent(
                message = state.message,
                onRetry = { onAction(EpisodeScheduleScreenState.Action.Retry) },
            )
        }
    }
}

@Composable
private fun ScheduleContent(
    state: EpisodeScheduleScreenState.Content,
) {
    val firstCardFocusRequester = rememberFocusRequesterOnLaunch()
    val firstFocusableSeasonIndex = remember(state.seasons) {
        state.seasons.indexOfFirst { it.announcementDate != null || it.episodes.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusGroup(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(
                items = state.seasons,
                key = { _, season -> "season_${season.seasonNumber}" },
            ) { index, season ->
                SeasonSection(
                    season = season,
                    firstCardFocusRequester = firstCardFocusRequester,
                    isFirstCard = index == firstFocusableSeasonIndex,
                )
            }
        }
        ScheduleSourceFooter()
    }
}

@Composable
private fun SeasonSection(
    season: EpisodeScheduleScreenState.Season,
    firstCardFocusRequester: FocusRequester,
    isFirstCard: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.episode_schedule_season, season.seasonNumber),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        if (season.announcementDate != null) {
            ScheduleCard(
                modifier = if (isFirstCard) {
                    Modifier.focusRequester(firstCardFocusRequester)
                } else {
                    Modifier
                },
                title = stringResource(R.string.episode_schedule_season_announcement),
                subtitle = stringResource(
                    R.string.episode_schedule_release_date,
                    season.announcementDateLabel.orEmpty(),
                ),
                testTag = "episode_schedule_season_${season.seasonNumber}",
            )
        }

        season.episodes.forEachIndexed { index, episode ->
            ScheduleCard(
                modifier = if (isFirstCard && season.announcementDate == null && index == 0) {
                    Modifier.focusRequester(firstCardFocusRequester)
                } else {
                    Modifier
                },
                title = stringResource(
                    R.string.episode_schedule_episode,
                    episode.episodeNumber,
                ),
                subtitle = if (episode.title.isBlank()) {
                    stringResource(
                        R.string.episode_schedule_release_date,
                        episode.airDateLabel,
                    )
                } else {
                    stringResource(
                        R.string.episode_schedule_episode_subtitle,
                        episode.title,
                        stringResource(
                            R.string.episode_schedule_release_date,
                            episode.airDateLabel,
                        ),
                    )
                },
                testTag = "episode_schedule_${season.seasonNumber}_${episode.episodeNumber}",
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    title: String,
    subtitle: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag(testTag)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScheduleEmptyContent(
    reason: EpisodeScheduleScreenState.EmptyReason,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.episode_schedule_empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = emptyReasonText(reason),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScheduleSourceFooter()
        }
    }
}

@Composable
private fun ScheduleErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { retryFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.episode_schedule_error_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvSafeButton(
                text = stringResource(R.string.episode_schedule_retry),
                onClick = onRetry,
                primary = true,
                modifier = Modifier.focusRequester(retryFocusRequester),
            )
        }
    }
}

@Composable
private fun ScheduleSourceFooter() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.episode_schedule_source_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.episode_schedule_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun emptyReasonText(reason: EpisodeScheduleScreenState.EmptyReason): String {
    return stringResource(
        when (reason) {
            EpisodeScheduleScreenState.EmptyReason.MissingCredentials ->
                R.string.episode_schedule_empty_missing_credentials

            EpisodeScheduleScreenState.EmptyReason.NoMatch ->
                R.string.episode_schedule_empty_no_match

            EpisodeScheduleScreenState.EmptyReason.NoUpcomingReleases ->
                R.string.episode_schedule_empty_no_upcoming
        },
    )
}
