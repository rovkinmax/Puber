package com.kino.puber.playertestfixtures.server

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermeticTestServerTest {

    @Test
    fun routesMatchMethodPathAndQueryWithoutQueueOrderAndRedactValues() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "serial",
                        path = "/v1/items",
                        query = mapOf("type" to "serial"),
                        response = HermeticTestServer.text("serial"),
                    ),
                    server.route(
                        id = "movie",
                        path = "/v1/items",
                        query = mapOf("type" to "movie"),
                        response = HermeticTestServer.text("movie"),
                    ),
                ),
            )

            assertEquals("serial", request(server.url("/v1/items", mapOf("type" to "serial"))).text)
            assertEquals("movie", request(server.url("/v1/items", mapOf("type" to "movie"))).text)

            val snapshot = server.requestJournal
            assertTrue(snapshot.entries.all { "type" in it.queryKeys })
            assertTrue(snapshot.entries.none { it.path.contains("serial") || it.path.contains("movie") })
            assertEquals(0, snapshot.activeRequestCount)
        }
    }

    @Test
    fun sequenceRedirectAndRangeResponsesAreDeterministic() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "sequence",
                        path = "/sequence",
                        response = HermeticTestServer.sequence(
                            HermeticTestServer.text("first", status = 503),
                            HermeticTestServer.text("second"),
                        ),
                    ),
                    server.route(
                        id = "redirect",
                        path = "/redirect",
                        response = HermeticTestServer.redirect("/sequence"),
                    ),
                    server.route(
                        id = "range",
                        path = "/range",
                        response = ResponsePlan.Range("abcdef".toByteArray()),
                    ),
                ),
            )

            assertEquals(503, request(server.url("/sequence")).status)
            assertEquals(200, request(server.url("/sequence")).status)
            val redirect = request(server.url("/redirect"), followRedirects = false)
            assertEquals(302, redirect.status)
            assertEquals("/sequence", redirect.location)
            val range = request(server.url("/range"), range = "bytes=1-3")
            assertEquals(206, range.status)
            assertArrayEquals("bcd".toByteArray(), range.bytes)
            assertEquals("bytes 1-3/6", range.contentRange ?: range.headers.toString())
        }
    }

    @Test
    fun unknownRoutesAreRecordedAndDelayedResponsesTrackActiveRequests() {
        HermeticTestServer().use { server ->
            server.start()
            val gate = CountDownLatch(1)
            server.reset(
                listOf(
                    server.route(
                        id = "delayed",
                        path = "/delayed",
                        response = HermeticTestServer.delayed(gate, "payload".toByteArray()),
                    ),
                ),
            )

            val unknown = request(server.url("/unexpected?token=secret"), followRedirects = false)
            assertEquals(404, unknown.status)
            val requestThread = Thread {
                request(server.url("/delayed"))
            }
            requestThread.start()
            eventually { server.activeRequestCount == 1 }
            assertTrue(server.requestJournal.entries.none { it.path.contains("secret") })
            gate.countDown()
            requestThread.join(5_000)
            assertFalse(requestThread.isAlive)
            server.awaitQuiescence()
            assertEquals(1, server.requestJournal.unknownRequests.size)
        }
    }

    @Test
    fun resetStartsNewGenerationOnlyAfterRequestsQuiesce() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "first",
                        path = "/first",
                        response = HermeticTestServer.text("first"),
                    ),
                ),
            )
            val firstGeneration = server.generationId
            request(server.url("/first"))
            server.reset(
                listOf(
                    server.route(
                        id = "second",
                        path = "/second",
                        response = HermeticTestServer.text("second"),
                    ),
                ),
            )
            assertTrue(server.generationId > firstGeneration)
            assertEquals(0, server.requestJournal.entries.size)
            assertEquals(404, request(server.url("/first")).status)
            assertEquals(200, request(server.url("/second")).status)
        }
    }

    @Test
    fun concurrentResetCannotReplaceRoutesBeforeRequestAdmission() {
        HermeticTestServer().use { server ->
            server.start()
            val admissionSelected = CountDownLatch(1)
            val allowAdmission = CountDownLatch(1)
            val allowResponse = CountDownLatch(1)
            server.beforeRequestAdmissionForTest = {
                admissionSelected.countDown()
                check(allowAdmission.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to admit selected route"
                }
            }
            server.reset(
                listOf(
                    server.route(
                        id = "delayed-old",
                        path = "/delayed-old",
                        response = HermeticTestServer.delayed(
                            gate = allowResponse,
                            body = "old".toByteArray(),
                        ),
                    ),
                    server.route(
                        id = "old-only",
                        path = "/old-only",
                        response = HermeticTestServer.text("old-only"),
                    ),
                ),
            )
            val firstGeneration = server.generationId
            val requestResult = AtomicReference<Response>()
            val requestFailure = AtomicReference<Throwable>()
            val requestThread = Thread {
                runCatching { request(server.url("/delayed-old")) }
                    .onSuccess(requestResult::set)
                    .onFailure(requestFailure::set)
            }
            requestThread.start()
            assertTrue(admissionSelected.await(5, TimeUnit.SECONDS))

            val resetFailure = AtomicReference<Throwable>()
            val resetThread = Thread {
                runCatching {
                    server.reset(
                        listOf(
                            server.route(
                                id = "new-only",
                                path = "/new-only",
                                response = HermeticTestServer.text("new-only"),
                            ),
                        ),
                    )
                }.onFailure(resetFailure::set)
            }
            resetThread.start()
            allowAdmission.countDown()
            resetThread.join(5_000)

            assertFalse(resetThread.isAlive)
            assertTrue(resetFailure.get() is IllegalStateException)
            assertEquals(firstGeneration, server.generationId)
            allowResponse.countDown()
            requestThread.join(5_000)
            assertFalse(requestThread.isAlive)
            assertNull(requestFailure.get())
            assertEquals("old", requestResult.get().text)
            assertEquals("old-only", request(server.url("/old-only")).text)
            assertEquals(404, request(server.url("/new-only")).status)

            server.awaitQuiescence()
            server.beforeRequestAdmissionForTest = {}
            server.reset(
                listOf(
                    server.route(
                        id = "new-only",
                        path = "/new-only",
                        response = HermeticTestServer.text("new-only"),
                    ),
                ),
            )

            assertTrue(server.generationId > firstGeneration)
            assertEquals(404, request(server.url("/old-only")).status)
            assertEquals("new-only", request(server.url("/new-only")).text)
            assertEquals(1, server.requestJournal.unknownRequests.size)
        }
    }

    @Test
    fun truncationAndDisconnectAreObservableWithoutLeakingActiveRequests() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "truncated",
                        path = "/truncated",
                        response = HermeticTestServer.truncated(
                            body = "complete".toByteArray(),
                            bytesToWrite = 2,
                        ),
                    ),
                    server.route(
                        id = "disconnected",
                        path = "/disconnected",
                        response = HermeticTestServer.disconnected(
                            body = "interrupted".toByteArray(),
                        ),
                    ),
                ),
            )

            val truncated = request(server.url("/truncated"))
            assertArrayEquals("co".toByteArray(), truncated.bytes)
            val disconnected = request(server.url("/disconnected"))
            assertTrue(disconnected.bytes.size < "interrupted".length)
            server.awaitQuiescence()
            assertEquals(0, server.requestJournal.activeRequestCount)
            assertEquals(2, server.requestJournal.entries.size)
        }
    }

    private fun eventually(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline && !predicate()) Thread.yield()
        assertTrue(predicate())
    }

    private fun request(
        url: String,
        followRedirects: Boolean = true,
        range: String? = null,
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = followRedirects
        range?.let { connection.setRequestProperty("Range", it) }
        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            Response(
                status = status,
                bytes = bytes,
                text = String(bytes),
                location = connection.getHeaderField("Location"),
                contentRange = connection.getHeaderField("Content-Range"),
                headers = connection.headerFields,
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(
        val status: Int,
        val bytes: ByteArray,
        val text: String,
        val location: String?,
        val contentRange: String?,
        val headers: Map<String, List<String>>,
    )
}
