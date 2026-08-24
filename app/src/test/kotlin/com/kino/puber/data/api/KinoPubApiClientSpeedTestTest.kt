package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class KinoPubApiClientSpeedTestTest {

    companion object {
        private const val TEST_CACHE_SIZE = 1024L * 1024L
        private const val STREAM_PROGRESS_TIMEOUT_SECONDS = 3L

        @JvmStatic
        @BeforeAll
        fun setUpAndroidLogging() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
        }

        @JvmStatic
        @AfterAll
        fun tearDownAndroidLogging() {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun streamSpeedTest_repeatsIdenticalUrl_withoutCacheOrResponseLogging(
        @TempDir cacheDir: Path,
    ) = runTest {
        val body = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
        val requests = AtomicInteger()
        val capturedHeaders = CopyOnWriteArrayList<ProbeHeaders>()

        withServer(
            handler = { exchange ->
                requests.incrementAndGet()
                capturedHeaders +=
                    ProbeHeaders(
                        cacheControl = exchange.requestHeaders.getFirst("Cache-Control"),
                        pragma = exchange.requestHeaders.getFirst("Pragma"),
                    )
                exchange.respond(status = 200, body = body)
            },
        ) { url ->
            val client = client(cacheDir)
            val first = client.streamSpeedTest(
                server = SpeedTestServer.AMSTERDAM,
                url = url,
                expectedBytes = body.size.toLong(),
            )
            val second = client.streamSpeedTest(
                server = SpeedTestServer.AMSTERDAM,
                url = url,
                expectedBytes = body.size.toLong(),
            )

            assertTrue(first.isSuccess, first.exceptionOrNull()?.stackTraceToString())
            assertTrue(second.isSuccess, second.exceptionOrNull()?.stackTraceToString())
        }

        assertEquals(2, requests.get())
        assertEquals(
            List(2) {
                ProbeHeaders(
                    cacheControl = "no-store, no-cache",
                    pragma = "no-cache",
                )
            },
            capturedHeaders,
        )
        assertTrue(cacheDir.toFile().walk().none { it.isFile }, "probe response was cached")
    }

    @Test
    fun streamSpeedTest_doesNotRetryRequestTimeout(
        @TempDir cacheDir: Path,
    ) = runTest {
        val requests = AtomicInteger()
        withServer(
            handler = { exchange ->
                if (requests.incrementAndGet() == 1) {
                    exchange.respond(status = 408, body = ByteArray(0))
                } else {
                    exchange.respond(status = 200, body = ByteArray(1))
                }
            },
        ) { url ->
            val result = client(cacheDir).streamSpeedTest(
                server = SpeedTestServer.AMSTERDAM,
                url = url,
                expectedBytes = 1L,
            )

            assertTrue(result.isFailure, "HTTP 408 must not be transparently retried")
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 408"))
            assertEquals(1, requests.get(), "timed-out request was transparently retried")
        }
    }

    @Test
    fun streamSpeedTest_reportsCompleteProgressAndMeasuredSpeed(
        @TempDir cacheDir: Path,
    ) = runTest {
        val body = ByteArray(64 * 1024) { 7 }
        val progress = mutableListOf<Long>()

        withServer(handler = { it.respond(status = 200, body = body) }) { url ->
            val result = client(cacheDir).streamSpeedTest(
                server = SpeedTestServer.MOSCOW,
                url = url,
                expectedBytes = body.size.toLong(),
                onProgress = { progress += it.downloadedBytes },
            )

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertEquals(body.size.toLong(), result.getOrThrow().downloadedBytes)
            assertEquals(body.size.toLong(), result.getOrThrow().expectedBytes)
            assertTrue(progress.first() == 0L)
            assertEquals(body.size.toLong(), progress.last())
            assertTrue(result.getOrThrow().megabitsPerSecond >= 0.0)
        }
    }

    @Test
    fun streamSpeedTest_emitsProgressBeforeResponseCompletes(
        @TempDir cacheDir: Path,
    ) = runTest {
        val firstChunk = ByteArray(16 * 1024) { 1 }
        val secondChunk = ByteArray(16 * 1024) { 2 }
        val progressObserved = CountDownLatch(1)
        val responseContinuedAfterProgress = AtomicBoolean()

        withServer(
            handler = { exchange ->
                exchange.sendResponseHeaders(
                    200,
                    (firstChunk.size + secondChunk.size).toLong(),
                )
                exchange.responseBody.use { output ->
                    output.write(firstChunk)
                    output.flush()
                    responseContinuedAfterProgress.set(
                        progressObserved.await(STREAM_PROGRESS_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                    output.write(secondChunk)
                }
            },
        ) { url ->
            val result = client(cacheDir).streamSpeedTest(
                server = SpeedTestServer.MOSCOW,
                url = url,
                expectedBytes = (firstChunk.size + secondChunk.size).toLong(),
                onProgress = {
                    if (it.downloadedBytes > 0L) {
                        progressObserved.countDown()
                    }
                },
            )

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        }

        assertTrue(
            responseContinuedAfterProgress.get(),
            "probe progress was emitted only after the full response completed",
        )
    }

    @Test
    fun streamSpeedTest_returnsFailureForHttpError(
        @TempDir cacheDir: Path,
    ) = runTest {
        withServer(handler = { it.respond(status = 503, body = ByteArray(0)) }) { url ->
            val result = client(cacheDir).streamSpeedTest(
                server = SpeedTestServer.AMSTERDAM,
                url = url,
                expectedBytes = 100,
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 503"))
        }
    }

    @Test
    fun streamSpeedTest_returnsFailureWhenBodyIsIncomplete(
        @TempDir cacheDir: Path,
    ) = runTest {
        val body = ByteArray(16 * 1024) { 5 }
        withServer(handler = { it.respond(status = 200, body = body) }) { url ->
            val result = client(cacheDir).streamSpeedTest(
                server = SpeedTestServer.AMSTERDAM,
                url = url,
                expectedBytes = body.size.toLong() + 1L,
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("incomplete"))
        }
    }

    @Test
    fun streamSpeedTest_rethrowsCancellation(
        @TempDir cacheDir: Path,
    ) = runTest {
        val body = ByteArray(256 * 1024) { 3 }
        val requests = AtomicInteger()
        withServer(
            handler = { exchange ->
                requests.incrementAndGet()
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output ->
                    repeat(64) {
                        output.write(body)
                        output.flush()
                        Thread.sleep(2)
                    }
                }
            },
        ) { url ->
            val result = async {
                client(cacheDir).streamSpeedTest(
                    server = SpeedTestServer.AMSTERDAM,
                    url = url,
                    expectedBytes = body.size.toLong() * 64,
                )
            }
            while (requests.get() == 0) {
                delay(1)
            }
            result.cancel()

            var propagatedCancellation: CancellationException? = null
            try {
                result.await()
            } catch (error: CancellationException) {
                propagatedCancellation = error
            }

            assertTrue(
                propagatedCancellation is CancellationException,
                "stream cancellation must be observable by the caller",
            )
        }
    }

    private fun client(cacheDir: Path): KinoPubApiClient {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } returns true

        val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
        every { preferences.getAccessToken() } returns null
        every { preferences.getRefreshToken() } returns null
        every { preferences.getUsername() } returns null
        every { preferences.getAndroidId() } returns null

        val transport = OkHttpClient.Builder()
            .cache(Cache(cacheDir.resolve("preconfigured-okhttp-cache").toFile(), TEST_CACHE_SIZE))
            .build()

        return KinoPubApiClient(
            okHttpClient = transport,
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = preferences,
            sessionEventBus = SessionEventBus(),
            mainApiBaseUrl = "http://127.0.0.1/",
        )
    }

    private suspend fun withServer(
        handler: (HttpExchange) -> Unit,
        block: suspend (url: String) -> Unit,
    ) {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        server.createContext("/probe", handler)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/probe")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: ByteArray) {
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private data class ProbeHeaders(
        val cacheControl: String?,
        val pragma: String?,
    )

}
