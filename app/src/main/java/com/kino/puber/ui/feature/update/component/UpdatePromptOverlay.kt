package com.kino.puber.ui.feature.update.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvDialogOverlay
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.ui.feature.update.model.UpdatePromptAction
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
import kotlinx.coroutines.delay

private val DialogWidth = 820.dp
private val DialogPadding = 24.dp
private val DialogCornerRadius = 18.dp
private val ContentSpacing = 16.dp
private val NotesMaxHeight = 180.dp
private const val FocusDelayMs = 100L

@Composable
internal fun UpdatePromptOverlay(
    state: UpdatePromptViewState,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == UpdatePromptViewState.Hidden) {
        return
    }

    val primaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state) {
        delay(FocusDelayMs)
        runCatching { primaryFocusRequester.requestFocus() }
    }

    TvDialogOverlay(onDismiss = { onAction(UpdatePromptAction.DismissClicked) }) {
        Card(
            modifier = modifier
                .width(DialogWidth)
                .padding(24.dp),
            shape = RoundedCornerShape(DialogCornerRadius),
        ) {
            Column(
                modifier = Modifier.padding(DialogPadding),
                verticalArrangement = Arrangement.spacedBy(ContentSpacing),
            ) {
                when (state) {
                    is UpdatePromptViewState.Available -> AvailableContent(
                        update = state.update,
                        primaryFocusRequester = primaryFocusRequester,
                        onAction = onAction,
                    )
                    is UpdatePromptViewState.Downloading -> DownloadingContent(state)
                    is UpdatePromptViewState.PermissionRequired -> PermissionRequiredContent(
                        primaryFocusRequester = primaryFocusRequester,
                        onAction = onAction,
                    )
                    is UpdatePromptViewState.Error -> ErrorContent(
                        state = state,
                        primaryFocusRequester = primaryFocusRequester,
                        onAction = onAction,
                    )
                    UpdatePromptViewState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun AvailableContent(
    update: AvailableUpdate,
    primaryFocusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
) {
    Header(
        title = stringResource(R.string.update_prompt_title),
        version = update.version.toString(),
    )
    ReleaseNotes(notes = update.releaseNotes)
    ActionRow {
        TvSafeButton(
            text = stringResource(R.string.update_prompt_update),
            onClick = { onAction(UpdatePromptAction.UpdateClicked) },
            primary = true,
            modifier = Modifier.focusRequester(primaryFocusRequester),
        )
        TvSafeButton(
            text = stringResource(R.string.update_prompt_not_now),
            onClick = { onAction(UpdatePromptAction.DismissClicked) },
        )
    }
}

@Composable
private fun DownloadingContent(state: UpdatePromptViewState.Downloading) {
    Header(
        title = stringResource(R.string.update_prompt_title),
        version = state.update.version.toString(),
    )
    Text(
        text = stringResource(R.string.update_prompt_downloading, state.progressPercent),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { state.progressPercent.coerceIn(0, 100) / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
    )
}

@Composable
private fun PermissionRequiredContent(
    primaryFocusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
) {
    Text(
        text = stringResource(R.string.update_prompt_permission_required_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.update_prompt_permission_required),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActionRow {
        TvSafeButton(
            text = stringResource(R.string.update_prompt_open_settings),
            onClick = { onAction(UpdatePromptAction.OpenInstallPermissionSettingsClicked) },
            primary = true,
            modifier = Modifier.focusRequester(primaryFocusRequester),
        )
        TvSafeButton(
            text = stringResource(R.string.update_prompt_retry_install),
            onClick = { onAction(UpdatePromptAction.RetryInstallClicked) },
        )
        TvSafeButton(
            text = stringResource(R.string.update_prompt_not_now),
            onClick = { onAction(UpdatePromptAction.DismissClicked) },
        )
    }
}

@Composable
private fun ErrorContent(
    state: UpdatePromptViewState.Error,
    primaryFocusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
) {
    Text(
        text = stringResource(R.string.update_prompt_error_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = state.message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActionRow {
        TvSafeButton(
            text = stringResource(
                if (state.canRetryInstall) {
                    R.string.update_prompt_retry_install
                } else {
                    R.string.update_prompt_retry
                }
            ),
            onClick = { onAction(UpdatePromptAction.RetryInstallClicked) },
            primary = true,
            modifier = Modifier.focusRequester(primaryFocusRequester),
        )
        TvSafeButton(
            text = stringResource(R.string.update_prompt_not_now),
            onClick = { onAction(UpdatePromptAction.DismissClicked) },
        )
    }
}

@Composable
private fun Header(title: String, version: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.update_prompt_version, version),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReleaseNotes(notes: String?) {
    val displayNotes = notes?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.update_prompt_notes_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = displayNotes,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = NotesMaxHeight)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}
