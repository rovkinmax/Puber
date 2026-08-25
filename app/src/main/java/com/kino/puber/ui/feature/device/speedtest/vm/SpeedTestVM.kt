package com.kino.puber.ui.feature.device.speedtest.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ConnectionTransportProvider
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.speedtest.SpeedTestEvent
import com.kino.puber.domain.interactor.speedtest.SpeedTestInteractor
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer
import com.kino.puber.domain.interactor.speedtest.SpeedTestServerMetadata
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestAction
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowState
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestSessionStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect

internal class SpeedTestVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val interactor: SpeedTestInteractor,
    private val resources: ResourceProvider,
    private val transportProvider: ConnectionTransportProvider,
) : PuberVM<SpeedTestViewState>(router) {

    override val initialViewState = SpeedTestViewState(
        transport = transportProvider.current(),
    )

    private var sessionJob: Job? = null
    private var sessionGeneration = 0L
    private val lastVisibleProgress = mutableMapOf<SpeedTestServer, Long>()
    private val lastPositiveMeasurements =
        mutableMapOf<SpeedTestServer, SpeedTestEvent.Progress>()

    override fun onStart() {
        launch {
            applyServerMetadata(interactor.serverMetadata())
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            SpeedTestAction.Start -> start()
            SpeedTestAction.Stop -> stop()
            else -> super.onAction(action)
        }
    }

    override fun onBackPressed() {
        if (stateValue.sessionStatus == SpeedTestSessionStatus.Running) {
            markCanceled()
            sessionJob?.cancel()
        }
        router.back()
    }

    private fun start() {
        if (sessionJob != null) return

        prepareSession()
        val generation = ++sessionGeneration
        val job = launch {
            runSession(generation)
        }
        sessionJob = job
        observeSessionCompletion(job)
    }

    private fun prepareSession() {
        lastVisibleProgress.clear()
        lastPositiveMeasurements.clear()
        updateViewState(
            stateValue.copy(
                transport = transportProvider.current(),
                rows = stateValue.rows.map { row ->
                    SpeedTestRowState(
                        server = row.server,
                        displayLabel = row.displayLabel,
                        isCurrentServer = row.isCurrentServer,
                    )
                },
                sessionStatus = SpeedTestSessionStatus.Running,
                canStart = false,
                canStop = true,
                sessionError = null,
            ),
        )
    }

    private suspend fun runSession(generation: Long) {
        try {
            interactor.run().collect { event ->
                if (isCurrentRunningSession(generation)) {
                    onEvent(event)
                }
            }
            if (isCurrentRunningSession(generation)) {
                updateViewState(
                    stateValue.copy(
                        sessionStatus = SpeedTestSessionStatus.Completed,
                        canStart = true,
                        canStop = false,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!isCurrentRunningSession(generation)) return
            val message = failureMessage(error)
            updateViewState(
                stateValue.copy(
                    sessionStatus = SpeedTestSessionStatus.Failed,
                    canStart = true,
                    canStop = false,
                    sessionError = message,
                ),
            )
        }
    }

    private fun observeSessionCompletion(job: Job) {
        job.invokeOnCompletion { cause ->
            if (sessionJob !== job) return@invokeOnCompletion
            sessionJob = null
            when {
                stateValue.sessionStatus == SpeedTestSessionStatus.Canceled ->
                    updateViewState(stateValue.copy(canStart = true))

                cause is CancellationException &&
                    stateValue.sessionStatus == SpeedTestSessionStatus.Running -> markCanceled()
            }
        }
    }

    private fun applyServerMetadata(metadata: List<SpeedTestServerMetadata>) {
        val selectedServer = metadata
            .filter { it.selected == SELECTED_OPTION }
            .singleOrNull()
            ?.server
        updateViewState(
            stateValue.copy(
                rows = stateValue.rows.map { row ->
                    val serverMetadata = metadata.firstOrNull { it.server == row.server }
                    row.copy(
                        displayLabel = serverMetadata?.label,
                        isCurrentServer = row.server == selectedServer,
                    )
                },
            ),
        )
    }

    private fun stop() {
        if (stateValue.sessionStatus != SpeedTestSessionStatus.Running) return
        markCanceled()
        sessionJob?.cancel()
    }

    private fun isCurrentRunningSession(generation: Long): Boolean =
        sessionGeneration == generation &&
            stateValue.sessionStatus == SpeedTestSessionStatus.Running

    private fun onEvent(event: SpeedTestEvent) {
        when (event) {
            is SpeedTestEvent.Started -> {
                lastVisibleProgress[event.server] = Long.MIN_VALUE
                lastPositiveMeasurements.remove(event.server)
                updateRow(event.server) {
                    copy(
                        status = SpeedTestRowStatus.Running,
                        downloadedBytes = 0L,
                        elapsedMillis = 0L,
                        megabitsPerSecond = 0.0,
                        errorMessage = null,
                    )
                }
            }

            is SpeedTestEvent.Progress -> {
                if (event.downloadedBytes > 0L) {
                    lastPositiveMeasurements[event.server] = event
                }
                val previous = lastVisibleProgress[event.server] ?: Long.MIN_VALUE
                val isTerminalProgress = event.downloadedBytes >= event.expectedBytes
                if (
                    isTerminalProgress ||
                    previous == Long.MIN_VALUE ||
                    event.elapsedMillis - previous >= PROGRESS_UPDATE_INTERVAL_MILLIS
                ) {
                    lastVisibleProgress[event.server] = event.elapsedMillis
                    updateMeasurement(
                        server = event.server,
                        status = SpeedTestRowStatus.Running,
                        downloadedBytes = event.downloadedBytes,
                        expectedBytes = event.expectedBytes,
                        elapsedMillis = event.elapsedMillis,
                        megabitsPerSecond = event.megabitsPerSecond,
                    )
                }
            }

            is SpeedTestEvent.Completed -> {
                lastVisibleProgress[event.server] = event.elapsedMillis
                updateMeasurement(
                    server = event.server,
                    status = SpeedTestRowStatus.Completed,
                    downloadedBytes = event.downloadedBytes,
                    expectedBytes = event.expectedBytes,
                    elapsedMillis = event.elapsedMillis,
                    megabitsPerSecond = event.megabitsPerSecond,
                )
            }

            is SpeedTestEvent.Failed -> applyFailure(event)
        }
    }

    private fun applyFailure(event: SpeedTestEvent.Failed) {
        val measurement = lastPositiveMeasurements[event.server]
        updateRow(event.server) {
            copy(
                status = SpeedTestRowStatus.Failed,
                downloadedBytes = measurement?.downloadedBytes ?: downloadedBytes,
                expectedBytes = measurement?.expectedBytes ?: expectedBytes,
                elapsedMillis = measurement?.elapsedMillis ?: elapsedMillis,
                megabitsPerSecond = measurement?.megabitsPerSecond ?: megabitsPerSecond,
                errorMessage = failureMessage(event.cause),
            )
        }
    }

    private fun failureMessage(cause: Throwable): String =
        errorHandler.map(cause).message.takeUnless(String::isBlank)
            ?: resources.getString(R.string.speed_test_failure)

    private fun updateMeasurement(
        server: SpeedTestServer,
        status: SpeedTestRowStatus,
        downloadedBytes: Long,
        expectedBytes: Long,
        elapsedMillis: Long,
        megabitsPerSecond: Double,
    ) {
        updateRow(server) {
            copy(
                status = status,
                downloadedBytes = downloadedBytes,
                expectedBytes = expectedBytes,
                elapsedMillis = elapsedMillis,
                megabitsPerSecond = megabitsPerSecond,
                errorMessage = null,
            )
        }
    }

    private fun updateRow(
        server: SpeedTestServer,
        update: SpeedTestRowState.() -> SpeedTestRowState,
    ) {
        updateViewState(
            stateValue.copy(
                rows = stateValue.rows.map { row ->
                    if (row.server == server) row.update() else row
                },
            ),
        )
    }

    private fun markCanceled() {
        updateViewState(
            stateValue.copy(
                rows = stateValue.rows.map { row ->
                    if (row.status == SpeedTestRowStatus.Running) {
                        row.copy(status = SpeedTestRowStatus.Canceled)
                    } else {
                        row
                    }
                },
                sessionStatus = SpeedTestSessionStatus.Canceled,
                canStart = sessionJob == null,
                canStop = false,
            ),
        )
    }

    override fun dispatchError(error: ErrorEntity) {
        val activeRows = stateValue.rows.map { row ->
            if (row.status == SpeedTestRowStatus.Running) {
                row.copy(
                    status = SpeedTestRowStatus.Failed,
                    errorMessage = error.message,
                )
            } else {
                row
            }
        }
        updateViewState(
            stateValue.copy(
                rows = activeRows,
                sessionStatus = SpeedTestSessionStatus.Failed,
                canStart = true,
                canStop = false,
                sessionError = error.message,
            ),
        )
        showMessage(error.message)
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val SELECTED_OPTION = 1
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
