package com.kino.puber.domain.interactor

import kotlinx.coroutines.flow.Flow

interface IAuthInteractor {

    fun getAuthState(): Flow<Boolean>
}