package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

private const val CLIENT_ID = "android"
private const val CLIENT_SECRET = BuildConfig.CLIENT_SECRET

// Убрать, заменив плагином в ktor
class KinoPubParametersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Проверяем, является ли это OAuth запросом по URL
        val isOAuthRequest = originalRequest.url.toString().contains("/oauth2/")

        if (!isOAuthRequest) {
            return chain.proceed(originalRequest)
        }

        // Добавляем CLIENT_ID и CLIENT_SECRET как query параметры
        val urlBuilder = originalRequest.url.newBuilder()

        // Проверяем, есть ли уже эти параметры в URL
        val existingClientId = originalRequest.url.queryParameter("client_id")
        val existingClientSecret = originalRequest.url.queryParameter("client_secret")

        if (existingClientId == null) {
            urlBuilder.addQueryParameter("client_id", CLIENT_ID)
        }
        if (existingClientSecret == null) {
            urlBuilder.addQueryParameter("client_secret", CLIENT_SECRET)
        }

        val newRequest = originalRequest.newBuilder()
            .url(urlBuilder.build())
            .build()

        return chain.proceed(newRequest)
    }
}
