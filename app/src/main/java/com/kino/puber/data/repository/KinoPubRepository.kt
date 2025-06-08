package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KinoPubRepository(
    private val client: KinoPubApiClient,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
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
                        cryptoPreferenceRepository.saveAccessToken(value.token.accessToken)
                        cryptoPreferenceRepository.saveRefreshToken(value.token.refreshToken)
                        emit(AuthState.Success)
                    }
                }
        }
    }
}