package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.core.logger.log
import com.kino.puber.data.api.network.CurlLogFormatter
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.core.build
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.core.writePacket
import io.ktor.utils.io.readRemaining
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.text.Charsets.UTF_8

private const val CLIENT_ID = "android"
private const val CLIENT_SECRET = BuildConfig.CLIENT_SECRET
private const val PADDING_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
private const val MAX_PADDING_LENGTH = 2048
private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
private const val MAX_LOG_BODY_SIZE = BYTES_PER_MEBIBYTE

val TrafficPaddingPlugin = createClientPlugin("TrafficPaddingPlugin") {
    onRequest { request, _ ->
        val length = kotlin.random.Random.nextInt(MAX_PADDING_LENGTH)
        val padding = buildString(length) {
            repeat(length) { append(PADDING_CHARS[kotlin.random.Random.nextInt(PADDING_CHARS.length)]) }
        }
        request.headers.append("random", padding)
    }
}

val KinoPubParametersPlugin = createClientPlugin("KinoPubParametersPlugin") {
    onRequest { request, _ ->
        val url = request.url.toString()
        val isOAuthRequest = url.contains("/oauth2/")

        if (!isOAuthRequest) return@onRequest

        val currentClientId = request.url.parameters["client_id"]
        val currentClientSecret = request.url.parameters["client_secret"]

        if (currentClientId == null) {
            request.url.parameters.append("client_id", CLIENT_ID)
        }

        if (currentClientSecret == null) {
            request.url.parameters.append("client_secret", CLIENT_SECRET)
        }
    }
}

val CurlLogger = createClientPlugin("CurlLogger") {
    val formatter = CurlLogFormatter()

    onRequest { request, _ ->
        val headers = request.headers.entries().flatMap { (name, values) ->
            values.map { value -> name to value }
        }
        val textBody = (request.body as? TextContent)?.text
        formatter.formatRequest(
            method = request.method.value,
            url = request.url.toString(),
            headers = headers,
            textBody = textBody,
        ).forEach { line -> log(line) }
    }

    onResponse { response ->
        val headers = response.headers.entries().flatMap { (name, values) ->
            values.map { value -> name to value }
        }
        val content = if (
            shouldSkipBodyLogging(
                host = response.request.url.host,
                path = response.request.url.encodedPath,
                contentType = response.headers[HttpHeaders.ContentType],
            )
        ) {
            "<body logging skipped>"
        } else {
            readTextLimited(response.bodyAsChannel(), MAX_LOG_BODY_SIZE)
        }
        formatter.formatResponse(
            status = response.status.toString(),
            url = response.request.url.toString(),
            headers = headers,
            body = content,
        ).forEach { line -> log(line) }
    }
}

/**
 * Reads up to maxSize characters from the channel and returns them as string.
 */
private suspend fun readTextLimited(channel: ByteReadChannel, maxSize: Long): String {
    val buffer = Buffer()
    var bytesCopied = 0L

    while (!channel.isClosedForRead && bytesCopied < maxSize) {
        val packet = channel.readRemaining(minOf(channel.availableForRead.toLong(), maxSize - bytesCopied))
        bytesCopied += packet.remaining
        buffer.writePacket(packet)
    }

    val byteArray = buffer.build().readByteArray()
    return try {
        byteArray.toString(UTF_8)
    } catch (_: Exception) {
        "<binary body or decode error>"
    }
}
private fun shouldSkipBodyLogging(host: String, path: String, contentType: String?): Boolean {
    val normalizedHost = host.lowercase()
    val normalizedPath = path.lowercase()
    val normalizedContentType = contentType.orEmpty().lowercase()

    return normalizedHost.isGitHubHost() ||
        normalizedPath.endsWith(".apk") ||
        normalizedPath.endsWith(".sha256") ||
        normalizedContentType.contains("application/vnd.android.package-archive") ||
        normalizedContentType.startsWith("application/octet-stream")
}

private fun String.isGitHubHost(): Boolean {
    return this == "github.com" ||
        endsWith(".github.com") ||
        this == "githubusercontent.com" ||
        endsWith(".githubusercontent.com")
}
