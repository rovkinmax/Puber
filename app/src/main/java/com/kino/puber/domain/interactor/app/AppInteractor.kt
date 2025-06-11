package com.kino.puber.domain.interactor.app

import com.kino.puber.data.api.auth.LogOutBus
import com.kino.puber.data.api.auth.LogOutEvent
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

internal class AppInteractor(
    private val cryptoRepository: ICryptoPreferenceRepository,
    private val logOutBus: LogOutBus,
) : IAppInteractor {
    override fun logOutEvet(): Flow<LogOutEvent> {
        return logOutBus.content()
            .onEach {
                cryptoRepository.clearAll()
            }
    }
}