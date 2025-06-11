package com.kino.puber.domain.interactor.app

import com.kino.puber.data.api.auth.LogOutEvent
import kotlinx.coroutines.flow.Flow

interface IAppInteractor {
    fun logOutEvet(): Flow<LogOutEvent>
}