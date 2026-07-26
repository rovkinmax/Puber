package com.kino.puber.data.api.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HistoryRequestTest {

    @Test
    fun page_buildsVerifiedReadRequest() {
        val request = HistoryRequest.page(page = 3)

        assertEquals("history", request.path)
        assertEquals(mapOf("page" to "3"), request.query)
        assertEquals("no-store", request.cacheControl)
    }

    @Test
    fun clearExactMedia_buildsVerifiedMediaRequest() {
        val request = HistoryRequest.clearExactMedia(mediaId = 73001)

        assertEquals("history/clear-for-media", request.path)
        assertEquals(mapOf("id" to "73001"), request.query)
        assertEquals("no-store", request.cacheControl)
    }

    @Test
    fun resolveUrl_composesEachBuiltInMainApiPresetExactlyOnce() {
        val baseUrls = listOf(
            "https://api.service-kp.com/v1/",
            "https://api.alador.space/v1/",
            "https://cdn-service.online/api/v1/",
        )
        val requests = listOf(
            HistoryRequest.page(page = 1) to "history",
            HistoryRequest.clearExactMedia(mediaId = 1) to "history/clear-for-media",
        )

        baseUrls.forEach { baseUrl ->
            requests.forEach { (request, expectedPath) ->
                assertEquals("$baseUrl$expectedPath", request.resolveUrl(baseUrl))
            }
        }
    }

    @Test
    fun resolveUrl_normalizesMissingOrRepeatedTrailingSeparator() {
        val request = HistoryRequest.clearExactMedia(mediaId = 1)

        assertEquals(
            "https://example.test/v1/history/clear-for-media",
            request.resolveUrl("https://example.test/v1"),
        )
        assertEquals(
            "https://example.test/v1/history/clear-for-media",
            request.resolveUrl("https://example.test/v1///"),
        )
    }

    @Test
    fun factories_rejectInvalidPaginationAndMediaIdentity() {
        assertThrows(IllegalArgumentException::class.java) { HistoryRequest.page(page = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            HistoryRequest.clearExactMedia(mediaId = 0)
        }
    }
}
