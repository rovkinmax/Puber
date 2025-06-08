package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import io.ktor.client.plugins.api.createClientPlugin

private const val CLIENT_ID = "android"
private const val CLIENT_SECRET = BuildConfig.CLIENT_SECRET

val KinoPubParametersPlugin = createClientPlugin("KinoPubParametersPlugin") {
    onRequest { request, _ ->
        val url = request.url.toString()
        val isOAuthRequest = url.contains("/oauth2/")

        if (!isOAuthRequest) return@onRequest

        val currentClientId = request.url.parameters["client_id"]
        val currentClientSecret = request.url.parameters["client_secret"]

        if (currentClientId == null) {
            request.url.parameters.append("client_id", CLIENT_ID)
        }

        if (currentClientSecret == null) {
            request.url.parameters.append("client_secret", CLIENT_SECRET)
        }
    }
}