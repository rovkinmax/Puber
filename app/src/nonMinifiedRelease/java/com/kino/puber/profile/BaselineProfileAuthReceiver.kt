package com.kino.puber.profile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kino.puber.data.repository.CryptoPreferenceRepository

internal class BaselineProfileAuthReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = CryptoPreferenceRepository(context.applicationContext)
        when (intent.action) {
            ACTION_SEED_AUTH -> seedAuth(repository, intent)
            else -> finish(Result.UnknownAction)
        }
    }

    private fun seedAuth(
        repository: CryptoPreferenceRepository,
        intent: Intent,
    ) {
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        val refreshToken = intent.getStringExtra(EXTRA_REFRESH_TOKEN).orEmpty()
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            finish(Result.MissingToken)
            return
        }

        repository.saveAccessToken(accessToken)
        repository.saveRefreshToken(refreshToken)
        intent.getStringExtra(EXTRA_USERNAME)
            ?.takeIf(String::isNotBlank)
            ?.let(repository::saveUsername)
        intent.getStringExtra(EXTRA_API_DOMAIN)
            ?.takeIf(String::isNotBlank)
            ?.let(repository::saveApiDomain)

        finish(Result.Ok)
    }

    private fun finish(result: Result, data: String? = null) {
        setResultCode(result.code)
        setResultData(data ?: result.name)
    }

    private enum class Result(val code: Int) {
        Ok(Activity.RESULT_OK),
        MissingToken(2),
        UnknownAction(3),
    }

    companion object {
        private const val ACTION_SEED_AUTH = "com.kino.puber.profile.SEED_AUTH"

        private const val EXTRA_ACCESS_TOKEN = "access_token"
        private const val EXTRA_REFRESH_TOKEN = "refresh_token"
        private const val EXTRA_USERNAME = "username"
        private const val EXTRA_API_DOMAIN = "api_domain"
    }
}
