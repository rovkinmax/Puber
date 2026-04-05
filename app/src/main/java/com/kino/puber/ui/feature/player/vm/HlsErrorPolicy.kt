package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException
import kotlin.math.min

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
        return min(loadErrorInfo.errorCount * 1_000L, 5_000L)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 5

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        val exception = loadErrorInfo.exception
        if (exception is HttpDataSource.InvalidResponseCodeException) {
            val code = exception.responseCode
            if (code == 400 || code == 502) {
                if (fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)) {
                    return LoadErrorHandlingPolicy.FallbackSelection(
                        LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                        60_000L,
                    )
                }
            }
        }
        return null
    }
}
