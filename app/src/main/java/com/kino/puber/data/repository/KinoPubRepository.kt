package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen

class KinoPubRepository(
    private val client: KinoPubApiClient,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
) : IKinoPubRepository {

    override fun getAuthState(): Flow<AuthState> = channelFlow {
        if (client.isAuthenticated()) {
            send(AuthState.Success)
            return@channelFlow
        }

        val codeResult = client.getDeviceLoginCode().first()
        if (codeResult.isFailure) throw codeResult.exceptionOrNull()!!

        val deviceCode = codeResult.getOrThrow().deviceCode
        send(AuthState.Code(deviceCode.userCode, deviceCode.verificationUri, deviceCode.expiresIn))

        flow {
            while (true) {
                delay(deviceCode.interval * 1000L)
                val result = client.getDeviceLoginStatus(deviceCode).first()
                if (result.isFailure) throw result.exceptionOrNull()!!

                val token = result.getOrThrow().token
                if (token != null) {
                    emit(token)
                    break
                } else {
                    throw IllegalStateException("Token is still null")
                }
            }
        }.retryWhen { cause, attempt ->
            attempt < MAX_RETRIES
        }.collect { token ->
            cryptoPreferenceRepository.saveAccessToken(token.accessToken)
            cryptoPreferenceRepository.saveRefreshToken(token.refreshToken)
            send(AuthState.Success)
        }
    }

    companion object {
        private const val MAX_RETRIES = 500L
    }
}