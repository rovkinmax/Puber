package com.kino.puber.domain.interactor.auth

import com.kino.puber.data.repository.IKinoPubRepository
import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.flow.Flow

internal class AuthInteractor(
    private val repository: IKinoPubRepository
) : IAuthInteractor {
    override fun getAuthState(): Flow<AuthState> = repository.getAuthState()
}