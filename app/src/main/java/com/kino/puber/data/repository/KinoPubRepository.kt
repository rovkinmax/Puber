package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KinoPubRepository(
    private val client: KinoPubApiClient,
) : IKinoPubRepository {

    override fun getAuthState(): Flow<AuthState> = flow {
        if (client.isAuthenticated()) {
            emit(AuthState.Success)
        } else {
            client.completeDeviceFlow()
                .collect { result ->
                    val value = result.getOrThrow()
                    if (value.token == null) {
                        emit(AuthState.Code(value.deviceCode.userCode))
                    } else {
                        emit(AuthState.Success)
                    }
                }
        }
    }
}