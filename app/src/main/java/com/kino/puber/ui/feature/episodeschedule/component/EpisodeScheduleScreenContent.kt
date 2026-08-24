package com.kino.puber.ui.feature.episodeschedule.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.TmdbSourceNotice
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        VideoGrid(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = state.grid,
            initialFocusedItemId = state.initialFocusedItemId,
        )
        TmdbSourceNotice(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
            TmdbSourceNotice()
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
            TmdbSourceNotice()
        }
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
