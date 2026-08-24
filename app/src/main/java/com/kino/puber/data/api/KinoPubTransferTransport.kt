package com.kino.puber.data.api

import android.net.ConnectivityManager
import com.kino.puber.data.api.network.createConnectivityPlugin
import com.kino.puber.domain.interactor.speedtest.SpeedTestProgress
import com.kino.puber.domain.interactor.speedtest.SpeedTestResult
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer
import com.kino.puber.domain.interactor.speedtest.calculateSpeedMbitPerSecond
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

internal class KinoPubTransferTransport(
    private val sharedHttpClient: HttpClient,
    okHttpClient: OkHttpClient,
    connectivityManager: ConnectivityManager,
) {
    private val speedTestHttpClient = createSpeedTestHttpClient(
        okHttpClient = okHttpClient,
        connectivityManager = connectivityManager,
    )

    suspend fun downloadUpdateAsset(
        url: String,
        targetFile: File,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File("${targetFile.absolutePath}.download")
        try {
            targetFile.parentFile?.mkdirs()
            if (tempFile.exists() && !tempFile.delete()) {
                throw IllegalStateException("Unable to delete stale update download")
            }

            val response = sharedHttpClient.get(url)
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Update download failed with HTTP ${response.status.value}")
            }

            val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var downloadedBytes = 0L
            var lastProgress = -1

            fun dispatchProgress(percent: Int) {
                val coercedPercent = percent.coerceIn(0, 100)
                if (coercedPercent != lastProgress) {
                    lastProgress = coercedPercent
                    onProgress(coercedPercent)
                }
            }

            if (totalBytes != null && totalBytes > 0L) {
                dispatchProgress(0)
            }

            tempFile.outputStream().buffered().use { output ->
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead == -1) {
                        break
                    }

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes != null && totalBytes > 0L) {
                        dispatchProgress(((downloadedBytes * 100L) / totalBytes).toInt())
                    }
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                if (targetFile.exists() && !targetFile.delete()) {
                    throw IllegalStateException("Unable to replace existing update download")
                }
                if (!tempFile.renameTo(targetFile)) {
                    throw IllegalStateException("Unable to finalize update download")
                }
            }

            dispatchProgress(100)
            Result.success(targetFile)
        } catch (error: CancellationException) {
            tempFile.delete()
            throw error
        } catch (error: Exception) {
            tempFile.delete()
            Result.failure(error)
        }
    }

    suspend fun streamSpeedTest(
        server: SpeedTestServer,
        url: String,
        onProgress: suspend (SpeedTestProgress) -> Unit,
        expectedBytes: Long,
    ): Result<SpeedTestResult> = withContext(Dispatchers.IO) {
        try {
            speedTestHttpClient.prepareRequest(url) {
                headers {
                    append(HttpHeaders.CacheControl, "no-store, no-cache")
                    append(HttpHeaders.Pragma, "no-cache")
                }
            }.execute { response ->
                ensureSpeedTestSuccess(response)
                val startedAt = System.nanoTime()
                onProgress(speedTestProgress(server, expectedBytes, downloadedBytes = 0L, elapsedMillis = 0L))
                val downloadedBytes = readSpeedTestBody(
                    response = response,
                    server = server,
                    expectedBytes = expectedBytes,
                    startedAt = startedAt,
                    onProgress = onProgress,
                )
                Result.success(
                    speedTestResult(
                        server = server,
                        downloadedBytes = downloadedBytes,
                        expectedBytes = expectedBytes,
                        elapsedMillis = elapsedMillisSince(startedAt),
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Result.failure(error)
        }
    }

    fun close() {
        speedTestHttpClient.close()
    }

    private fun ensureSpeedTestSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Speed test failed with HTTP ${response.status.value}")
        }
    }

    private suspend fun readSpeedTestBody(
        response: HttpResponse,
        server: SpeedTestServer,
        expectedBytes: Long,
        startedAt: Long,
        onProgress: suspend (SpeedTestProgress) -> Unit,
    ): Long {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var downloadedBytes = 0L

        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(buffer)
            if (bytesRead == -1) break
            if (bytesRead > 0) {
                downloadedBytes += bytesRead
                onProgress(
                    speedTestProgress(
                        server = server,
                        expectedBytes = expectedBytes,
                        downloadedBytes = downloadedBytes,
                        elapsedMillis = elapsedMillisSince(startedAt),
                    ),
                )
            }
        }
        return downloadedBytes
    }

    private fun speedTestProgress(
        server: SpeedTestServer,
        expectedBytes: Long,
        downloadedBytes: Long,
        elapsedMillis: Long,
    ) = SpeedTestProgress(
        server = server,
        downloadedBytes = downloadedBytes,
        expectedBytes = expectedBytes,
        elapsedMillis = elapsedMillis,
        megabitsPerSecond = calculateSpeedMbitPerSecond(
            downloadedBytes = downloadedBytes,
            elapsedMillis = elapsedMillis,
        ),
    )

    private fun speedTestResult(
        server: SpeedTestServer,
        downloadedBytes: Long,
        expectedBytes: Long,
        elapsedMillis: Long,
    ): SpeedTestResult {
        if (downloadedBytes != expectedBytes) {
            throw IllegalStateException(
                "Speed test incomplete: expected $expectedBytes bytes, downloaded $downloadedBytes",
            )
        }
        return SpeedTestResult(
            server = server,
            downloadedBytes = downloadedBytes,
            expectedBytes = expectedBytes,
            elapsedMillis = elapsedMillis,
            megabitsPerSecond = calculateSpeedMbitPerSecond(
                downloadedBytes = downloadedBytes,
                elapsedMillis = elapsedMillis,
            ),
        )
    }

    private fun elapsedMillisSince(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)

    private fun createSpeedTestHttpClient(
        okHttpClient: OkHttpClient,
        connectivityManager: ConnectivityManager,
    ): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
            config {
                cache(null)
                retryOnConnectionFailure(false)
            }
        }

        install(createConnectivityPlugin(connectivityManager))
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT
            requestTimeoutMillis = READ_TIMEOUT
            socketTimeoutMillis = READ_TIMEOUT
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT = 60_000L
        const val READ_TIMEOUT = 120_000L
        const val DOWNLOAD_BUFFER_SIZE = 8 * 1024
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
