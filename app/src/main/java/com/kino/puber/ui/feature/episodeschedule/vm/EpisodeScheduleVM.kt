package com.kino.puber.ui.feature.episodeschedule.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenState
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleUIMapper
import kotlinx.coroutines.CancellationException

internal class EpisodeScheduleVM(
    router: AppRouter,
    private val params: EpisodeScheduleScreenParams,
    private val interactor: EpisodeScheduleInteractor,
    private val mapper: EpisodeScheduleUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<EpisodeScheduleScreenState>(router) {

    override val initialViewState = EpisodeScheduleScreenState.Loading

    override fun onStart() {
        loadSchedule()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            CommonAction.RetryClicked,
            EpisodeScheduleScreenState.Action.Retry,
            -> loadSchedule()

            else -> super.onAction(action)
        }
    }

    private fun loadSchedule() = launch {
        updateViewState(EpisodeScheduleScreenState.Loading)
        try {
            val result = interactor.getSchedule(params.imdbId)
            updateViewState(
                when (result) {
                    is EpisodeScheduleResult.Available -> mapper.map(params, result)
                    else -> mapper.mapEmpty(result)
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            updateViewState(mapper.mapError(error))
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is EpisodeScheduleScreenState.Loading) {
            updateViewState(EpisodeScheduleScreenState.Error(error.message))
        } else {
            showMessage(error.message)
        }
    }
}
