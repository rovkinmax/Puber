package com.kino.puber.data.repository

internal interface IAppUpdateRepository {

    suspend fun getAvailableUpdate(currentVersionName: String): Result<AvailableUpdate?>
}
