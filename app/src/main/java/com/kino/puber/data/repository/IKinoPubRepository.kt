package com.kino.puber.data.repository

import kotlinx.coroutines.flow.Flow

interface IKinoPubRepository {

    fun isAuth(): Flow<Boolean>
}