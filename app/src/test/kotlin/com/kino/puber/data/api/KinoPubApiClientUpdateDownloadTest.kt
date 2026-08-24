package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class KinoPubApiClientUpdateDownloadTest {

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 3L

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
    fun downloadUpdateAsset_streamsBytesAndDeduplicatesProgress(
        @TempDir cacheDir: Path,
    ) = runTest {
        val body = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
        val target = cacheDir.resolve("update.apk").toFile().apply {
            writeBytes(byteArrayOf(9))
        }
        val progress = mutableListOf<Int>()

        withServer(handler = { it.respond(status = 200, body = body) }) { url ->
            val result = client(cacheDir).downloadUpdateAsset(
                url = url,
                targetFile = target,
                onProgress = progress::add,
            )

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertEquals(target, result.getOrThrow())
        }

        assertTrue(body.contentEquals(target.readBytes()))
        assertEquals(0, progress.first())
        assertEquals(100, progress.last())
        assertEquals(progress.distinct(), progress)
        assertFalse(downloadFileFor(target).exists())
    }

    @Test
    fun downloadUpdateAsset_cleansTemporaryFileOnHttpFailure(
        @TempDir cacheDir: Path,
    ) = runTest {
        val originalBody = byteArrayOf(4, 5, 6)
        val target = cacheDir.resolve("update.apk").toFile().apply {
            writeBytes(originalBody)
        }
        val temporaryFile = downloadFileFor(target).apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        withServer(handler = { it.respond(status = 404, body = ByteArray(0)) }) { url ->
            val result = client(cacheDir).downloadUpdateAsset(
                url = url,
                targetFile = target,
                onProgress = {},
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 404"))
        }

        assertTrue(originalBody.contentEquals(target.readBytes()))
        assertFalse(temporaryFile.exists())
    }

    @Test
    fun downloadUpdateAsset_cleansTemporaryFileAndRethrowsCancellation(
        @TempDir cacheDir: Path,
    ) = runTest {
        val requestStarted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val originalBody = byteArrayOf(4, 5, 6)
        val target = cacheDir.resolve("update.apk").toFile().apply {
            writeBytes(originalBody)
        }
        val temporaryFile = downloadFileFor(target).apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        withServer(
            handler = { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output ->
                    output.write(ByteArray(8 * 1024) { 7 })
                    output.flush()
                    requestStarted.countDown()
                    releaseResponse.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    output.write(ByteArray(8 * 1024) { 8 })
                }
            },
        ) { url ->
            val result = async(Dispatchers.IO) {
                client(cacheDir).downloadUpdateAsset(
                    url = url,
                    targetFile = target,
                    onProgress = {},
                )
            }
            assertTrue(
                requestStarted.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "update request did not start",
            )
            result.cancel()
            releaseResponse.countDown()

            var propagatedCancellation: CancellationException? = null
            try {
                result.await()
            } catch (error: CancellationException) {
                propagatedCancellation = error
            }

            assertTrue(
                propagatedCancellation is CancellationException,
                "update cancellation must be observable by the caller",
            )
        }

        assertTrue(originalBody.contentEquals(target.readBytes()))
        assertFalse(temporaryFile.exists())
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

        return KinoPubApiClient(
            okHttpClient = OkHttpClient(),
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
        server.createContext("/update.apk", handler)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/update.apk")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: ByteArray) {
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private fun downloadFileFor(target: java.io.File): java.io.File =
        java.io.File("${target.absolutePath}.download")
}
