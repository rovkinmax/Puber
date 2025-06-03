package com.kino.puber.domain.interactor

import com.kino.puber.data.repository.IKinoPubRepository
import kotlinx.coroutines.flow.Flow

class AuthInteractor(
    private val repository: IKinoPubRepository
) : IAuthInteractor {

    override fun getAuthState(): Flow<Boolean> = repository.isAuth()
}