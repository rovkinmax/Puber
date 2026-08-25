package com.kino.puber.ui.feature.device.speedtest.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ConnectionTransport
import com.kino.puber.core.system.ConnectionTransportProvider
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.speedtest.SpeedTestEvent
import com.kino.puber.domain.interactor.speedtest.SpeedTestInteractor
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer
import com.kino.puber.domain.interactor.speedtest.SpeedTestServerMetadata
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestAction
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestSessionStatus
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class SpeedTestVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val interactor = mockk<SpeedTestInteractor>()
    private val resources = mockk<ResourceProvider>(relaxed = true)
    private val transportProvider = mockk<ConnectionTransportProvider>()

    @Test
    fun onStart_preservesKnownLabelsAndMarksExactlyOneSelectedRow() = runTest {
        every { transportProvider.current() } returns ConnectionTransport.Wifi
        coEvery { interactor.serverMetadata() } returns listOf(
            SpeedTestServerMetadata(
                server = SpeedTestServer.AMSTERDAM,
                optionId = 1,
                label = "Amsterdam API",
                selected = 0,
            ),
            SpeedTestServerMetadata(
                server = SpeedTestServer.MOSCOW,
                optionId = 2,
                label = "Moscow API",
                selected = 1,
            ),
        )

        val vm = createVM()
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("Amsterdam API", "Moscow API"),
            vm.testStateValue.rows.map { it.displayLabel },
        )
        assertEquals(1, vm.testStateValue.rows.count { it.isCurrentServer })
        assertTrue(
            vm.testStateValue.rows
                .first { it.server == SpeedTestServer.MOSCOW }
                .isCurrentServer,
        )

        every { interactor.run() } returns emptyFlow()
        vm.onAction(SpeedTestAction.Start)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("Amsterdam API", "Moscow API"),
            vm.testStateValue.rows.map { it.displayLabel },
        )
        assertEquals(1, vm.testStateValue.rows.count { it.isCurrentServer })
    }

    @Test
    fun onStart_leavesAllRowsUnmarkedForUnknownOrMalformedSelection() = runTest {
        every { transportProvider.current() } returns ConnectionTransport.Unknown
        coEvery { interactor.serverMetadata() } returns SpeedTestServer.knownServers.map { server ->
            SpeedTestServerMetadata(
                server = server,
                optionId = server.locationId,
                label = "${server.name} API",
                selected = 0,
            )
        }

        val unknownVm = createVM()
        unknownVm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(0, unknownVm.testStateValue.rows.count { it.isCurrentServer })
        assertTrue(unknownVm.testStateValue.rows.all { it.displayLabel != null })

        coEvery { interactor.serverMetadata() } returns SpeedTestServer.knownServers.map { server ->
            SpeedTestServerMetadata(
                server = server,
                optionId = server.locationId,
                label = "${server.name} API",
                selected = 1,
            )
        }

        val malformedVm = createVM()
        malformedVm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(0, malformedVm.testStateValue.rows.count { it.isCurrentServer })
    }

    @Test
    fun start_localizesFailureWithoutRawMessageAndPreservesLaterCompletion() = runTest {
        val rawFailure = IllegalStateException("raw-failure-sentinel")
        every { transportProvider.current() } returns ConnectionTransport.Wifi
        every { errorHandler.map(rawFailure) } returns ErrorEntity(
            message = "Локализованная ошибка",
            code = "test",
        )
        every { interactor.run() } returns flowOf(
            SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM),
            SpeedTestEvent.Progress(
                server = SpeedTestServer.AMSTERDAM,
                downloadedBytes = 50,
                expectedBytes = 100,
                elapsedMillis = 1_000,
                megabitsPerSecond = 0.4,
            ),
            SpeedTestEvent.Failed(
                server = SpeedTestServer.AMSTERDAM,
                cause = rawFailure,
            ),
            SpeedTestEvent.Started(SpeedTestServer.MOSCOW),
            SpeedTestEvent.Completed(
                server = SpeedTestServer.MOSCOW,
                downloadedBytes = 100,
                expectedBytes = 100,
                elapsedMillis = 1_000,
                megabitsPerSecond = 0.8,
            ),
        )

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        testScheduler.advanceUntilIdle()

        val state = vm.testStateValue
        assertEquals(SpeedTestSessionStatus.Completed, state.sessionStatus)
        assertTrue(state.canStart)
        assertFalse(state.canStop)
        assertEquals(
            SpeedTestRowStatus.Failed,
            state.rows.first { it.server == SpeedTestServer.AMSTERDAM }.status,
        )
        assertEquals(
            "Локализованная ошибка",
            state.rows.first { it.server == SpeedTestServer.AMSTERDAM }.errorMessage,
        )
        assertFalse(
            state.rows.any { it.errorMessage == rawFailure.message } ||
                state.sessionError == rawFailure.message,
        )
        assertEquals(
            SpeedTestRowStatus.Completed,
            state.rows.first { it.server == SpeedTestServer.MOSCOW }.status,
        )
        verify(exactly = 1) { errorHandler.map(rawFailure) }
        verify(exactly = 0) { resources.getString(R.string.speed_test_failure) }
    }

    @Test
    fun start_failedRowRetainsPositiveProgressSuppressedByVisibleThrottle() = runTest {
        val failure = IllegalStateException("post-progress-failure")
        every { transportProvider.current() } returns ConnectionTransport.Wifi
        every { errorHandler.map(failure) } returns ErrorEntity(
            message = "Локализованная ошибка",
            code = "test",
        )
        every { interactor.run() } returns flowOf(
            SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM),
            SpeedTestEvent.Progress(
                server = SpeedTestServer.AMSTERDAM,
                downloadedBytes = 0,
                expectedBytes = 100,
                elapsedMillis = 0,
                megabitsPerSecond = 0.0,
            ),
            SpeedTestEvent.Progress(
                server = SpeedTestServer.AMSTERDAM,
                downloadedBytes = 25,
                expectedBytes = 100,
                elapsedMillis = 250,
                megabitsPerSecond = 0.8,
            ),
            SpeedTestEvent.Failed(
                server = SpeedTestServer.AMSTERDAM,
                cause = failure,
            ),
        )

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        testScheduler.advanceUntilIdle()

        val row = vm.testStateValue.rows
            .first { it.server == SpeedTestServer.AMSTERDAM }
        assertEquals(SpeedTestRowStatus.Failed, row.status)
        assertEquals(25, row.downloadedBytes)
        assertEquals(100, row.expectedBytes)
        assertEquals(250, row.elapsedMillis)
        assertEquals(0.8, row.megabitsPerSecond)
        assertEquals("Локализованная ошибка", row.errorMessage)
    }

    @Test
    fun start_usesLocalizedFallbackWhenMappedFailureMessageIsBlank() = runTest {
        val rawFailure = IllegalStateException("raw-blank-sentinel")
        every { transportProvider.current() } returns ConnectionTransport.Unknown
        every { errorHandler.map(rawFailure) } returns ErrorEntity(
            message = " ",
            code = "test",
        )
        every { resources.getString(R.string.speed_test_failure) } returns "Тест не выполнен"
        every { interactor.run() } returns flowOf(
            SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM),
            SpeedTestEvent.Failed(
                server = SpeedTestServer.AMSTERDAM,
                cause = rawFailure,
            ),
        )

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "Тест не выполнен",
            vm.testStateValue.rows
                .first { it.server == SpeedTestServer.AMSTERDAM }
                .errorMessage,
        )
        assertFalse(
            vm.testStateValue.rows.any { it.errorMessage == rawFailure.message } ||
                vm.testStateValue.sessionError == rawFailure.message,
        )
        verify(exactly = 1) { errorHandler.map(rawFailure) }
        verify(exactly = 1) { resources.getString(R.string.speed_test_failure) }
    }

    @Test
    fun stop_cancelsOnlyCurrentSessionAndMarksActiveRowCanceled() = runTest {
        every { transportProvider.current() } returns ConnectionTransport.Ethernet
        val gate = CompletableDeferred<Unit>()
        every { interactor.run() } returns flow {
            emit(SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM))
            emit(
                SpeedTestEvent.Progress(
                    server = SpeedTestServer.AMSTERDAM,
                    downloadedBytes = 20,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.16,
                ),
            )
            gate.await()
        }

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        assertEquals(SpeedTestSessionStatus.Running, vm.testStateValue.sessionStatus)
        assertFalse(vm.testStateValue.canStart)
        assertTrue(vm.testStateValue.canStop)

        vm.onAction(SpeedTestAction.Stop)
        testScheduler.advanceUntilIdle()

        assertEquals(SpeedTestSessionStatus.Canceled, vm.testStateValue.sessionStatus)
        assertEquals(
            SpeedTestRowStatus.Canceled,
            vm.testStateValue.rows.first { it.server == SpeedTestServer.AMSTERDAM }.status,
        )
        assertTrue(vm.testStateValue.canStart)
        assertFalse(vm.testStateValue.canStop)
        assertEquals(0, vm.testStateValue.rows.count { it.status == SpeedTestRowStatus.Running })
    }

    @Test
    fun start_ignoresDelayedCompletionFromCanceledPreviousSession() = runTest {
        every { transportProvider.current() } returns ConnectionTransport.Ethernet
        val releaseCanceledSession = CompletableDeferred<Unit>()
        val completeRestartedSession = CompletableDeferred<Unit>()
        every { interactor.run() } returnsMany listOf(
            flow {
                emit(SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM))
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        releaseCanceledSession.await()
                    }
                }
            },
            flow {
                emit(SpeedTestEvent.Started(SpeedTestServer.MOSCOW))
                completeRestartedSession.await()
                emit(
                    SpeedTestEvent.Completed(
                        server = SpeedTestServer.MOSCOW,
                        downloadedBytes = 100,
                        expectedBytes = 100,
                        elapsedMillis = 1_000,
                        megabitsPerSecond = 0.8,
                    ),
                )
            },
        )

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        testScheduler.runCurrent()

        vm.onAction(SpeedTestAction.Stop)
        vm.onAction(SpeedTestAction.Start)
        testScheduler.runCurrent()

        assertEquals(SpeedTestSessionStatus.Running, vm.testStateValue.sessionStatus)
        assertEquals(
            SpeedTestRowStatus.Running,
            vm.testStateValue.rows.first { it.server == SpeedTestServer.MOSCOW }.status,
        )

        releaseCanceledSession.complete(Unit)
        testScheduler.runCurrent()

        assertEquals(SpeedTestSessionStatus.Running, vm.testStateValue.sessionStatus)
        assertEquals(
            SpeedTestRowStatus.Running,
            vm.testStateValue.rows.first { it.server == SpeedTestServer.MOSCOW }.status,
        )

        completeRestartedSession.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(SpeedTestSessionStatus.Completed, vm.testStateValue.sessionStatus)
        assertEquals(
            SpeedTestRowStatus.Completed,
            vm.testStateValue.rows.first { it.server == SpeedTestServer.MOSCOW }.status,
        )
        assertTrue(vm.testStateValue.canStart)
        assertFalse(vm.testStateValue.canStop)
    }

    @Test
    fun progress_updatesKeepTypedMeasurementAndTransport() = runTest {
        every { transportProvider.current() } returns ConnectionTransport.Cellular
        val gate = CompletableDeferred<Unit>()
        every { interactor.run() } returns flow {
            emit(SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM))
            emit(
                SpeedTestEvent.Progress(
                    server = SpeedTestServer.AMSTERDAM,
                    downloadedBytes = 75,
                    expectedBytes = 100,
                    elapsedMillis = 2_000,
                    megabitsPerSecond = 0.3,
                ),
            )
            gate.await()
        }

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        testScheduler.advanceUntilIdle()

        val row = vm.testStateValue.rows.first { it.server == SpeedTestServer.AMSTERDAM }
        assertEquals(ConnectionTransport.Cellular, vm.testStateValue.transport)
        assertEquals(SpeedTestRowStatus.Running, row.status)
        assertEquals(75, row.downloadedBytes)
        assertEquals(100, row.expectedBytes)
        assertEquals(0.3, row.megabitsPerSecond)
        assertEquals(SpeedTestSessionStatus.Running, vm.testStateValue.sessionStatus)
        vm.testCancelScope()
    }

    @Test
    fun back_cancelsSessionBeforeReturningToSettings() = runTest {
        every { transportProvider.current() } returns ConnectionTransport.Wifi
        val gate = CompletableDeferred<Unit>()
        every { interactor.run() } returns flow {
            emit(SpeedTestEvent.Started(SpeedTestServer.MOSCOW))
            gate.await()
        }

        val vm = createVM()
        vm.onAction(SpeedTestAction.Start)
        vm.onBackPressed()

        assertEquals(SpeedTestSessionStatus.Canceled, vm.testStateValue.sessionStatus)
        assertEquals(
            SpeedTestRowStatus.Canceled,
            vm.testStateValue.rows.first { it.server == SpeedTestServer.MOSCOW }.status,
        )
        verify { router.back() }
    }

    private fun createVM(): SpeedTestVM = SpeedTestVM(
        router = router,
        errorHandler = errorHandler,
        interactor = interactor,
        resources = resources,
        transportProvider = transportProvider,
    )
}
