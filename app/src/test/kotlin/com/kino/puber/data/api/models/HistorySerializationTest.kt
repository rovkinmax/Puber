package com.kino.puber.data.api.models

import com.kino.puber.data.api.history.HistoryPageResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistorySerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun movieFixture_decodesVerifiedHistoryAndProgressShape() {
        val response = decodeHistoryPage("history/history-page-movie.json")
        val history = response.items.single()
        val video = requireNotNull(history.video)
        val watching = requireNotNull(video.watching)

        assertEquals(1, response.pagination.current)
        assertEquals(20, response.pagination.perpage)
        assertEquals(1, response.pagination.total)
        assertEquals(1, response.pagination.totalItems)
        assertNull(history.recordId)
        assertEquals(72001, history.item.id)
        assertEquals(ItemType.MOVIE, history.item.type)
        assertEquals(73001, video.id)
        assertEquals(2, video.number)
        assertEquals(5_400, video.duration)
        assertEquals(0, video.watched)
        assertEquals(1_200, watching.time)
        assertEquals(5_400, watching.duration)
        assertEquals(0, watching.status)
        assertEquals("4102444810", watching.updatedAt)
        assertEquals(1_200, history.time)
        assertEquals("4102444810", history.updated)
        assertNull(history.season)
    }

    @Test
    fun seriesFixture_decodesExactSeasonAndEpisodeShape() {
        val response = decodeHistoryPage("history/history-page-series.json")
        val history = response.items.single()
        val video = requireNotNull(history.video)
        val watching = requireNotNull(video.watching)

        assertNull(history.recordId)
        assertEquals(82001, history.item.id)
        assertEquals(ItemType.SERIAL, history.item.type)
        assertEquals(4, history.season)
        assertEquals(83001, video.id)
        assertEquals(9, video.number)
        assertEquals(2_700, video.duration)
        assertEquals(1, video.watched)
        assertEquals(2_700, watching.time)
        assertEquals(2_700, watching.duration)
        assertEquals(1, watching.status)
        assertEquals("4105296010", history.updated)
    }

    @Test
    fun fixtures_tolerateUnknownResponseFields() {
        assertEquals(1, decodeHistoryPage("history/history-page-movie.json").items.size)
        assertEquals(1, decodeHistoryPage("history/history-page-series.json").items.size)
    }

    @Test
    fun serverContractFixture_decodesHistoryEnvelopeAndMediaShape() {
        val response = json.decodeFromString<HistoryPageResponse>(
            readFixture("history/history-page-server-contract.json"),
        ).toModel()
        val history = response.items.single()
        val video = requireNotNull(history.video)

        assertEquals(1, response.items.size)
        assertEquals(92001, history.item.id)
        assertNull(history.recordId)
        assertEquals(93001, video.id)
        assertEquals(2, video.number)
        assertEquals(1_200, video.watching?.time)
        assertEquals(4_800, video.watching?.duration)
        assertEquals(0, video.watched)
        assertEquals("411111111", history.updated)
    }

    @Test
    fun deletedServerEntry_isNotPublishedToHistoryUi() {
        val response = json.decodeFromString<HistoryPageResponse>(
            """
            {
              "history": [{
                "counter": 1,
                "first_seen": 1,
                "item": {"id": 92001, "title": "Synthetic Deleted Movie", "type": "movie"},
                "last_seen": 2,
                "media": {"id": 93001, "number": 1, "snumber": 0, "duration": 2700},
                "time": 100,
                "deleted": true
              }],
              "pagination": {"current": 1, "perpage": 20, "total": 1, "total_items": 1}
            }
            """.trimIndent(),
        ).toModel()

        assertTrue(response.items.isEmpty())
    }

    @Test
    fun missingExactCoordinates_remainNullableForNonPlayableMapping() {
        val response = json.decodeFromString<HistoryPageResponse>(
            """
            {
              "history": [{
                "counter": 1,
                "first_seen": 1,
                "item": {"id": 92001, "title": "Synthetic Incomplete Series", "type": "serial"},
                "last_seen": 2,
                "media": {"id": 93001, "number": 9, "snumber": 0, "duration": 2700},
                "time": 100,
                "deleted": false
              }],
              "pagination": {"current": 1, "perpage": 20, "total": 1, "total_items": 1}
            }
            """.trimIndent(),
        ).toModel()

        val history = response.items.single()
        assertNull(history.season)
        assertEquals(9, history.video?.number)
    }

    @Test
    fun missingMediaId_isRejected() {
        val malformed = """
            {
              "history": [{
                "counter": 1,
                "first_seen": 1,
                "item": {"id": 92001, "title": "Synthetic Invalid Movie", "type": "movie"},
                "last_seen": 2,
                "media": {"number": 1, "snumber": 0, "duration": 2700},
                "time": 100,
                "deleted": false
              }],
              "pagination": {"current": 1, "perpage": 20, "total": 1, "total_items": 1}
            }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<HistoryPageResponse>(malformed)
        }
    }

    @Test
    fun fixtures_containOnlyClearlySyntheticContent() {
        val fixtures = listOf(
            readFixture("history/history-page-movie.json"),
            readFixture("history/history-page-series.json"),
        )

        fixtures.forEach { fixture ->
            val normalized = fixture.lowercase()
            assertTrue(normalized.contains("synthetic"))
            assertTrue(normalized.contains("example.test"))
            assertFalse(normalized.contains("authorization"))
            assertFalse(normalized.contains("bearer "))
            assertFalse(normalized.contains("access_token"))
            assertFalse(normalized.contains("refresh_token"))
        }
    }

    private fun decodeHistoryPage(path: String): PaginatedResponse<History> =
        json.decodeFromString<HistoryPageResponse>(readFixture(path)).toModel()

    private fun readFixture(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing test fixture: $path"
        }.readText()
}
