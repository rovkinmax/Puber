package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.domain.interactor.auth.model.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first

class KinoPubRepository(
    private val client: KinoPubApiClient,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
) : IKinoPubRepository {

    override fun getAuthState(): Flow<AuthState> = channelFlow {
        if (client.isAuthenticated()) {
            send(AuthState.Success)
            return@channelFlow
        }

        while (true) {
            val codeResult = client.getDeviceLoginCode().first()
            if (codeResult.isFailure) throw codeResult.exceptionOrNull()!!

            val deviceCode = codeResult.getOrThrow().deviceCode
            send(AuthState.Code(deviceCode.userCode, deviceCode.verificationUri, deviceCode.expiresIn))

            val deadline = System.currentTimeMillis() + deviceCode.expiresIn * 1000L
            var authenticated = false

            while (System.currentTimeMillis() < deadline) {
                delay(deviceCode.interval * 1000L)
                try {
                    val result = client.getDeviceLoginStatus(deviceCode).first()
                    val token = result.getOrNull()?.token
                    if (token != null) {
                        cryptoPreferenceRepository.saveAccessToken(token.accessToken)
                        cryptoPreferenceRepository.saveRefreshToken(token.refreshToken)
                        authenticated = true
                        break
                    }
                } catch (_: Exception) {
                    // Polling error — continue until deadline
                }
            }

            if (authenticated) {
                send(AuthState.Success)
                return@channelFlow
            }
            // Code expired → loop restarts with new device code
        }
    }
}