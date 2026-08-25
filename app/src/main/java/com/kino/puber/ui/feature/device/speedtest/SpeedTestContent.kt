package com.kino.puber.ui.feature.device.speedtest

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.system.ConnectionTransport
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestAction
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowState
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestSessionStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestViewState

internal const val SPEED_TEST_START_TAG = "speed_test_start"
internal const val SPEED_TEST_STOP_TAG = "speed_test_stop"
internal const val SPEED_TEST_AMSTERDAM_TAG = "speed_test_amsterdam"
internal const val SPEED_TEST_MOSCOW_TAG = "speed_test_moscow"

@Composable
internal fun SpeedTestContent(
    state: SpeedTestViewState,
    onAction: (UIAction) -> Unit,
) {
    val startFocusRequester = rememberFocusRequesterOnLaunch()

    RestoreStartFocusOnTerminal(
        sessionStatus = state.sessionStatus,
        startFocusRequester = startFocusRequester,
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SpeedTestHeader(transport = state.transport)
            SpeedTestRows(rows = state.rows)
            SpeedTestControls(
                canStart = state.canStart,
                canStop = state.canStop,
                startFocusRequester = startFocusRequester,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun RestoreStartFocusOnTerminal(
    sessionStatus: SpeedTestSessionStatus,
    startFocusRequester: FocusRequester,
) {
    LaunchedEffect(sessionStatus) {
        if (
            sessionStatus == SpeedTestSessionStatus.Completed ||
            sessionStatus == SpeedTestSessionStatus.Failed ||
            sessionStatus == SpeedTestSessionStatus.Canceled
        ) {
            startFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun SpeedTestHeader(transport: ConnectionTransport) {
    Text(
        text = stringResource(R.string.speed_test_title),
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(
        text = stringResource(
            R.string.speed_test_connection,
            transportLabel(transport),
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SpeedTestRows(rows: List<SpeedTestRowState>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            SpeedTestRow(row = row)
        }
    }
}

@Composable
private fun SpeedTestControls(
    canStart: Boolean,
    canStop: Boolean,
    startFocusRequester: FocusRequester,
    onAction: (UIAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onAction(SpeedTestAction.Start) },
            enabled = canStart,
            modifier = Modifier
                .focusRequester(startFocusRequester)
                .testTag(SPEED_TEST_START_TAG),
        ) {
            Text(stringResource(R.string.speed_test_start))
        }
        Button(
            onClick = { onAction(SpeedTestAction.Stop) },
            enabled = canStop,
            modifier = Modifier.testTag(SPEED_TEST_STOP_TAG),
        ) {
            Text(stringResource(R.string.speed_test_stop))
        }
    }
}

@Composable
private fun SpeedTestRow(
    row: SpeedTestRowState,
) {
    val tag = when (row.server) {
        SpeedTestServer.AMSTERDAM -> SPEED_TEST_AMSTERDAM_TAG
        SpeedTestServer.MOSCOW -> SPEED_TEST_MOSCOW_TAG
    }
    val progress = if (row.expectedBytes > 0L) {
        (row.downloadedBytes.toFloat() / row.expectedBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedTestRowHeader(row = row)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            SpeedTestRowByteProgress(row = row)
            SpeedTestRowResult(row = row)
        }
    }
}

@Composable
private fun SpeedTestRowHeader(row: SpeedTestRowState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.displayLabel ?: serverLabel(row.server),
            style = MaterialTheme.typography.titleMedium,
        )
        if (row.isCurrentServer) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.speed_test_current_server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = rowStatusLabel(row),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeedTestRowByteProgress(row: SpeedTestRowState) {
    if (row.status == SpeedTestRowStatus.Idle) return

    val context = LocalContext.current
    Text(
        text = stringResource(
            R.string.speed_test_byte_progress,
            Formatter.formatShortFileSize(context, row.downloadedBytes),
            Formatter.formatShortFileSize(context, row.expectedBytes),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SpeedTestRowResult(row: SpeedTestRowState) {
    when (row.status) {
        SpeedTestRowStatus.Completed,
        SpeedTestRowStatus.Running,
        -> if (row.megabitsPerSecond > 0.0) {
            Text(
                text = stringResource(
                    R.string.speed_test_result,
                    row.megabitsPerSecond,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SpeedTestRowStatus.Failed -> {
            if (row.megabitsPerSecond > 0.0) {
                Text(
                    text = stringResource(
                        R.string.speed_test_result,
                        row.megabitsPerSecond,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = row.errorMessage ?: stringResource(R.string.speed_test_failure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SpeedTestRowStatus.Canceled -> Text(
            text = stringResource(R.string.speed_test_canceled),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SpeedTestRowStatus.Idle -> Unit
    }
}

@Composable
private fun transportLabel(transport: ConnectionTransport): String = stringResource(
    when (transport) {
        ConnectionTransport.Ethernet -> R.string.speed_test_transport_ethernet
        ConnectionTransport.Wifi -> R.string.speed_test_transport_wifi
        ConnectionTransport.Cellular -> R.string.speed_test_transport_cellular
        ConnectionTransport.Unknown -> R.string.speed_test_transport_unknown
    },
)

@Composable
private fun serverLabel(server: SpeedTestServer): String = stringResource(
    when (server) {
        SpeedTestServer.AMSTERDAM -> R.string.speed_test_server_amsterdam
        SpeedTestServer.MOSCOW -> R.string.speed_test_server_moscow
    },
)

@Composable
private fun rowStatusLabel(row: SpeedTestRowState): String = stringResource(
    when (row.status) {
        SpeedTestRowStatus.Idle -> R.string.speed_test_status_idle
        SpeedTestRowStatus.Running -> R.string.speed_test_status_running
        SpeedTestRowStatus.Completed -> R.string.speed_test_status_completed
        SpeedTestRowStatus.Failed -> R.string.speed_test_status_failed
        SpeedTestRowStatus.Canceled -> R.string.speed_test_status_canceled
    },
)

internal class SpeedTestPreviewProvider : PreviewParameterProvider<SpeedTestViewState> {
    override val values: Sequence<SpeedTestViewState> = sequenceOf(
        SpeedTestViewState(),
        SpeedTestViewState(
            transport = ConnectionTransport.Wifi,
            rows = listOf(
                SpeedTestRowState(
                    server = SpeedTestServer.AMSTERDAM,
                    displayLabel = "Amsterdam API",
                    isCurrentServer = true,
                    status = SpeedTestRowStatus.Running,
                    downloadedBytes = 50,
                    expectedBytes = 100,
                    megabitsPerSecond = 12.5,
                ),
                SpeedTestRowState(server = SpeedTestServer.MOSCOW),
            ),
            sessionStatus = SpeedTestSessionStatus.Running,
            canStart = false,
            canStop = true,
        ),
        SpeedTestViewState(
            rows = listOf(
                SpeedTestRowState(
                    server = SpeedTestServer.AMSTERDAM,
                    status = SpeedTestRowStatus.Completed,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    megabitsPerSecond = 20.0,
                ),
                SpeedTestRowState(
                    server = SpeedTestServer.MOSCOW,
                    status = SpeedTestRowStatus.Failed,
                    errorMessage = "Сервер недоступен",
                ),
            ),
            sessionStatus = SpeedTestSessionStatus.Completed,
        ),
        SpeedTestViewState(
            rows = listOf(
                SpeedTestRowState(
                    server = SpeedTestServer.AMSTERDAM,
                    status = SpeedTestRowStatus.Canceled,
                ),
                SpeedTestRowState(server = SpeedTestServer.MOSCOW),
            ),
            sessionStatus = SpeedTestSessionStatus.Canceled,
        ),
    )
}

@Preview(
    showBackground = true,
    device = Devices.TV_1080p,
    widthDp = 1920,
    heightDp = 1080,
)
@Composable
private fun SpeedTestContentPreview(
    @PreviewParameter(SpeedTestPreviewProvider::class) state: SpeedTestViewState,
) {
    PuberTheme {
        SpeedTestContent(state = state, onAction = {})
    }
}
