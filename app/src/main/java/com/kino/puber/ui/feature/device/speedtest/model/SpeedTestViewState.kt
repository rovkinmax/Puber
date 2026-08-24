package com.kino.puber.ui.feature.device.speedtest.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.system.ConnectionTransport
import com.kino.puber.domain.interactor.speedtest.SPEED_TEST_EXPECTED_BYTES
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer

@Immutable
internal data class SpeedTestViewState(
    val transport: ConnectionTransport = ConnectionTransport.Unknown,
    val currentServer: SpeedTestServer? = null,
    val rows: List<SpeedTestRowState> = SpeedTestServer.knownServers.map(::SpeedTestRowState),
    val sessionStatus: SpeedTestSessionStatus = SpeedTestSessionStatus.Idle,
    val canStart: Boolean = true,
    val canStop: Boolean = false,
    val sessionError: String? = null,
)

@Immutable
internal data class SpeedTestRowState(
    val server: SpeedTestServer,
    val displayLabel: String? = null,
    val isCurrentServer: Boolean = false,
    val status: SpeedTestRowStatus = SpeedTestRowStatus.Idle,
    val downloadedBytes: Long = 0L,
    val expectedBytes: Long = SPEED_TEST_EXPECTED_BYTES,
    val elapsedMillis: Long = 0L,
    val megabitsPerSecond: Double = 0.0,
    val errorMessage: String? = null,
)

internal enum class SpeedTestSessionStatus {
    Idle,
    Running,
    Completed,
    Failed,
    Canceled,
}

internal enum class SpeedTestRowStatus {
    Idle,
    Running,
    Completed,
    Failed,
    Canceled,
}
