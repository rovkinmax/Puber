package com.kino.puber.data.repository

import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.flow.Flow

interface IKinoPubRepository {

    fun isAuth(): Flow<AuthState>
}