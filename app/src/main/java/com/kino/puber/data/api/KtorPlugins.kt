package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.core.logger.log
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.content.TextContent
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
    val maxBodySize = 1024 * 1024L // 1MB

    onRequest { request, _ ->
        val curl = StringBuilder("curl")

        curl.append(" -X ").append(request.method.value)

        request.headers.entries().forEach { (key, values) ->
            values.forEach { value ->
                curl.append(" -H \"$key: $value\"")
            }
        }

        val body = request.body
        if (body is TextContent) {
            val data = body.text.replace("\n", "\\n")
            curl.append(" --data $'").append(data).append("'")
        } else {
            curl.append(" --data-binary '<non-text or unknown body>'")
        }

        curl.append(" \"").append(request.url).append("\"")

        log("╭--- cURL (${request.url})")
        log(curl.toString())
        log("╰--- (copy & paste to terminal)")
    }

    onResponse { response ->
        val responseBody = response.bodyAsChannel()
        val content = readTextLimited(responseBody, maxBodySize)

        log("<-- ${response.status} ${response.request.url}")
        response.headers.entries().forEach { (key, values) ->
            values.forEach { value ->
                log("$key: $value")
            }
        }

        log("")
        log(content)
        log("<-- END HTTP (${content.length}-char body)")
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