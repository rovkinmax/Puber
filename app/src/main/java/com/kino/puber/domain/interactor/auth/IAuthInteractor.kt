package com.kino.puber.domain.interactor.auth

import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.flow.Flow

interface IAuthInteractor {

    fun getAuthState(): Flow<AuthState>
}