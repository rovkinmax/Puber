package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KinoPubRepository(
    private val client: KinoPubClient,
) : IKinoPubRepository {

    override fun isAuth(): Flow<Boolean> = flow {
        emit(client.isAuthenticated())
    }
}