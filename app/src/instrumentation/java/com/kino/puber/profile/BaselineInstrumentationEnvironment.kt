package com.kino.puber.profile

import android.content.Context
import com.kino.puber.BuildConfig
import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig

internal object BaselineInstrumentationEnvironment {

    const val MOCK_HOST = "127.0.0.1"
    const val MOCK_SCHEME = "http"

    fun configure(context: Context) {
        KinoPubConfig.setPinnedEndpoint(
            ApiEndpointPreset(
                domain = "$MOCK_HOST:${BuildConfig.BASELINE_MOCK_PORT}",
                apiHost = MOCK_HOST,
                mainBaseUrl = "$MOCK_SCHEME://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_PORT}/v1/",
                oauthBaseUrl = "$MOCK_SCHEME://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_PORT}/oauth2/",
                extraBaseUrl = "$MOCK_SCHEME://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_PORT}/",
            )
        )
        BaselineNetworkBlocker.install(context, mockOrigin())
    }

    fun mockOrigin(): String =
        "$MOCK_SCHEME://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_PORT}"

    fun clearNetworkCaches(context: Context) {
        listOfNotNull(
            context.cacheDir.resolve("okhttpcache"),
            context.cacheDir.resolve("image_cache"),
            context.externalCacheDir?.resolve("media_cache"),
        ).forEach { cacheDirectory ->
            check(!cacheDirectory.exists() || cacheDirectory.deleteRecursively()) {
                "Failed to clear instrumentation cache: $cacheDirectory"
            }
        }
    }
}
