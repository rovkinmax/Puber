package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException
import java.io.IOException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(UnstableApi::class)
internal class HlsErrorPolicyTest {

    private val policy = HlsErrorPolicy()

    @Test
    fun retryDelay_isLinearAndCapped_forRetryableErrors() {
        assertEquals(1_000L, policy.getRetryDelayMsFor(errorInfo(errorCount = 1)))
        assertEquals(2_000L, policy.getRetryDelayMsFor(errorInfo(errorCount = 2)))
        assertEquals(5_000L, policy.getRetryDelayMsFor(errorInfo(errorCount = 5)))
        assertEquals(5_000L, policy.getRetryDelayMsFor(errorInfo(errorCount = 9)))
        assertEquals(1_000L, policy.getRetryDelayMsFor(errorInfo(invalidResponse(404))))
    }

    @Test
    fun retryDelay_isUnset_forParserAndFileNotFoundErrors() {
        assertEquals(
            C.TIME_UNSET,
            policy.getRetryDelayMsFor(errorInfo(exception = ParserException.createForMalformedManifest("bad", null))),
        )
        assertEquals(
            C.TIME_UNSET,
            policy.getRetryDelayMsFor(errorInfo(exception = FileNotFoundException("missing"))),
        )
    }

    @Test
    fun minimumRetryCount_isFive() {
        assertEquals(5, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun fallback_selectsTrackForBadRequestAndBadGateway_whenTrackIsAvailable() {
        listOf(400, 502).forEach { status ->
            val fallback = policy.getFallbackSelectionFor(
                fallbackOptions(numberOfTracks = 2),
                errorInfo(exception = invalidResponse(status)),
            )

            assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK, fallback?.type)
            assertEquals(60_000L, fallback?.exclusionDurationMs)
        }
    }

    @Test
    fun fallback_isAbsent_whenStatusOrTrackAvailabilityDoesNotQualify() {
        assertNull(
            policy.getFallbackSelectionFor(
                fallbackOptions(numberOfTracks = 2),
                errorInfo(exception = invalidResponse(404)),
            ),
        )
        assertNull(
            policy.getFallbackSelectionFor(
                fallbackOptions(numberOfTracks = 1),
                errorInfo(exception = invalidResponse(400)),
            ),
        )
    }

    private fun errorInfo(
        exception: IOException = IOException("transient"),
        errorCount: Int = 1,
    ): LoadErrorHandlingPolicy.LoadErrorInfo {
        return LoadErrorHandlingPolicy.LoadErrorInfo(
            mockk<LoadEventInfo>(),
            mockk<MediaLoadData>(),
            exception,
            errorCount,
        )
    }

    private fun fallbackOptions(numberOfTracks: Int) = LoadErrorHandlingPolicy.FallbackOptions(
        1,
        0,
        numberOfTracks,
        0,
    )

    private fun invalidResponse(status: Int) = HttpDataSource.InvalidResponseCodeException(
        status,
        "failure",
        null,
        emptyMap(),
        mockk(),
        byteArrayOf(),
    )
}
