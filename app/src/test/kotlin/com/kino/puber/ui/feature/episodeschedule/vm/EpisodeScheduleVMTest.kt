package com.kino.puber.ui.feature.episodeschedule.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.model.SnackbarMessage
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.domain.model.ScheduleProvider
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenState
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleUIMapper
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class EpisodeScheduleVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val interactor = mockk<EpisodeScheduleInteractor>()
    private val mapper = mockk<EpisodeScheduleUIMapper>()
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val params = EpisodeScheduleScreenParams(
        itemId = 42,
        title = "Series",
        imdbId = "tt123",
    )

    @Test
    fun load_mapsTypedEmptyReasons_withoutTreatingThemAsErrors() = runTest {
        coEvery { interactor.getSchedule("tt123") } returns EpisodeScheduleResult.MissingCredentials
        every { mapper.mapEmpty(EpisodeScheduleResult.MissingCredentials) } returns
            EpisodeScheduleScreenState.Empty(EpisodeScheduleScreenState.EmptyReason.MissingCredentials)

        val vm = createVM()
        vm.testOnStart()
        advanceUntilIdle()

        assertEquals(
            EpisodeScheduleScreenState.Empty(EpisodeScheduleScreenState.EmptyReason.MissingCredentials),
            vm.testStateValue,
        )
    }

    @Test
    fun load_distinguishesNoMatchAndNoUpcomingReasons() = runTest {
        coEvery { interactor.getSchedule("tt123") } returns EpisodeScheduleResult.NoMatch
        every { mapper.mapEmpty(EpisodeScheduleResult.NoMatch) } returns
            EpisodeScheduleScreenState.Empty(EpisodeScheduleScreenState.EmptyReason.NoMatch)

        val vm = createVM()
        vm.testOnStart()
        advanceUntilIdle()

        assertInstanceOf(EpisodeScheduleScreenState.Empty::class.java, vm.testStateValue)
        assertEquals(
            EpisodeScheduleScreenState.EmptyReason.NoMatch,
            (vm.testStateValue as EpisodeScheduleScreenState.Empty).reason,
        )
    }

    @Test
    fun retry_reexecutesScheduleLoad_afterFailure() = runTest {
        coEvery { interactor.getSchedule("tt123") } throws IllegalStateException("temporary")
        val vm = createVM()
        vm.testOnStart()
        advanceUntilIdle()
        assertInstanceOf(EpisodeScheduleScreenState.Error::class.java, vm.testStateValue)

        coEvery { interactor.getSchedule("tt123") } returns EpisodeScheduleResult.NoUpcomingReleases
        every { mapper.mapEmpty(EpisodeScheduleResult.NoUpcomingReleases) } returns
            EpisodeScheduleScreenState.Empty(EpisodeScheduleScreenState.EmptyReason.NoUpcomingReleases)

        vm.onAction(EpisodeScheduleScreenState.Action.Retry)
        advanceUntilIdle()

        assertEquals(
            EpisodeScheduleScreenState.EmptyReason.NoUpcomingReleases,
            (vm.testStateValue as EpisodeScheduleScreenState.Empty).reason,
        )
    }

    @Test
    fun dispatchError_replacesLoadingWithError() {
        val vm = createVM()

        dispatchError(vm, ErrorEntity(message = "load failed", code = "test"))

        assertEquals(EpisodeScheduleScreenState.Error("load failed"), vm.testStateValue)
    }

    @Test
    fun dispatchError_preservesContentAndShowsMessage() = runTest {
        val available = mockk<EpisodeScheduleResult.Available>()
        val content = EpisodeScheduleScreenState.Content(
            title = "Series",
            provider = ScheduleProvider.TMDB,
            seasons = emptyList(),
            grid = VideoGridUIState(emptyList()),
        )
        coEvery { interactor.getSchedule("tt123") } returns available
        every { mapper.map(params, available) } returns content
        val vm = createVM()
        vm.testOnStart()
        advanceUntilIdle()

        dispatchError(vm, ErrorEntity(message = "refresh failed", code = "test"))

        assertEquals(content, vm.testStateValue)
        assertEquals("refresh failed", (vm.testMessageValue as SnackbarMessage.Short).message)
    }

    private fun createVM(): EpisodeScheduleVM {
        every { errorHandler.proceed(any()) } returns {}
        every { mapper.mapError(any()) } returns EpisodeScheduleScreenState.Error("error")
        return EpisodeScheduleVM(
            router = router,
            params = params,
            interactor = interactor,
            mapper = mapper,
            errorHandler = errorHandler,
        )
    }

    private fun dispatchError(vm: EpisodeScheduleVM, error: ErrorEntity) {
        EpisodeScheduleVM::class.java
            .getDeclaredMethod("dispatchError", ErrorEntity::class.java)
            .apply { isAccessible = true }
            .invoke(vm, error)
    }
}
