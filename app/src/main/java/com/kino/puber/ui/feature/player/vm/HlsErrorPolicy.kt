package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException
import kotlin.math.min

private const val RETRY_BACKOFF_STEP_MS = 1_000L
private const val MAX_RETRY_BACKOFF_MS = 5_000L
private const val MINIMUM_RETRY_COUNT = 5
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_BAD_GATEWAY = 502
private const val TRACK_FALLBACK_EXCLUSION_MS = 60_000L

/**
 * Custom error handling policy for HLS streams:
 * - 5 retries with linear backoff (1s → 5s cap)
 * - No retry for parse/file-not-found errors
 * - Blacklist failing video variants on HTTP 400/502 for 60s
 */
@UnstableApi
internal class HlsErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (error is androidx.media3.common.ParserException || error is FileNotFoundException) {
            return C.TIME_UNSET
        }
        return min(loadErrorInfo.errorCount * RETRY_BACKOFF_STEP_MS, MAX_RETRY_BACKOFF_MS)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MINIMUM_RETRY_COUNT

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        val exception = loadErrorInfo.exception
        if (exception is HttpDataSource.InvalidResponseCodeException &&
            exception.responseCode in setOf(HTTP_BAD_REQUEST, HTTP_BAD_GATEWAY) &&
            fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)
        ) {
            return LoadErrorHandlingPolicy.FallbackSelection(
                LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                TRACK_FALLBACK_EXCLUSION_MS,
            )
        }
        return null
    }
}
