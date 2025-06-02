package com.kino.puber.core.logger

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class CurlLoggingInterceptor(
    private val isLogEnabled: Boolean,
    private val isLogRequest: Boolean = true,
    private val isLogResponse: Boolean = true,
    private val maxBodySize: Long = 1024 * 1024, // 1MB limit for body logging
) : Interceptor {

    companion object {
        private const val TYPE_FOR_IGNORE = "multipart"
        private val UTF8 = Charset.forName("UTF-8")
    }

    private var curlOptions: String? = null

    private val logger: Logger = object : Logger {
        override fun print(message: String) {
            Timber.tag("Puber:").d(message)
        }
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isLogEnabled) {
            return chain.proceed(chain.request())
        }

        val startNs = System.nanoTime()
        val request = chain.request()

        try {
            if (isLogRequest) {
                logRequest(request)
            }

            val response = chain.proceed(request)

            return if (isLogResponse) {
                logResponse(response, startNs)
            } else {
                response
            }
        } catch (e: Exception) {
            logger.print("<-- HTTP FAILED: $e")
            throw e
        }
    }

    private fun logRequest(request: Request) {
        val curlCmd = buildCurlCommand(request)
        printCurlCommand(request.url.toString(), curlCmd)
    }

    private fun buildCurlCommand(request: Request): String {
        val curlCmd = StringBuilder("curl")

        curlOptions?.let { options ->
            curlCmd.append(" ").append(options)
        }

        curlCmd.append(" -X ").append(request.method)

        val compressed = appendHeaders(request.headers, curlCmd)
        appendRequestBody(request, curlCmd)

        curlCmd.append(if (compressed) " --compressed " else " ")
            .append("\"").append(request.url).append("\"")

        return curlCmd.toString()
    }

    private fun appendHeaders(headers: Headers, curlCmd: StringBuilder): Boolean {
        var compressed = false

        for (i in 0 until headers.size) {
            val name = headers.name(i)
            var value = headers.value(i)

            if (value.isNotEmpty() && value.startsWith("\"") && value.endsWith("\"")) {
                value = "\\\"" + value.substring(1, value.length - 1) + "\\\""
            }

            if ("Accept-Encoding".equals(name, ignoreCase = true) &&
                "gzip".equals(value, ignoreCase = true)
            ) {
                compressed = true
            }

            curlCmd.append(" -H \"").append(name).append(": ").append(value).append("\"")
        }

        return compressed
    }

    private fun appendRequestBody(request: Request, curlCmd: StringBuilder) {
        request.body?.let { body ->
            if (shouldIgnoreBody(body.contentType()?.type, request.headers)) {
                curlCmd.append(" --data $'====== BODY CONTAINS FILE ======'")
                return
            }

            val buffer = Buffer()
            try {
                body.writeTo(buffer)
                val charset = body.contentType()?.charset(UTF8) ?: UTF8

                val bodyData = when {
                    buffer.size > maxBodySize -> " --data $'====== BODY TOO LARGE (${buffer.size} bytes) ======'"
                    !isPlaintext(buffer.clone()) -> " --data $'====== BINARY DATA (${buffer.size} bytes) ======'"
                    else ->
                        // try to keep to a single line and use a subshell to preserve any line breaks
                        " --data $'" + buffer.readString(charset).replace("\n", "\\n") + "'"
                }

                curlCmd.append(bodyData)
            } catch (e: Exception) {
                curlCmd.append(" --data $'====== ERROR READING BODY: ${e.message} ======'")
            }
        }
    }

    private fun shouldIgnoreBody(contentType: String?, headers: Headers): Boolean {
        return contentType == TYPE_FOR_IGNORE ||
                contentType?.contains(TYPE_FOR_IGNORE) == true ||
                headers["Content-Type"]?.contains(TYPE_FOR_IGNORE) == true
    }

    private fun printCurlCommand(url: String, curlCmd: String) {
        logger.print("╭--- cURL ($url)")
        logger.print(curlCmd)
        logger.print("╰--- (copy and paste the above line to a terminal)")
    }

    private fun logResponse(response: Response, startNs: Long): Response {
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val contentLength = response.body?.contentLength() ?: -1L

        printResponseMetadata(response, contentLength, tookMs)
        printResponseHeaders(response.headers)

        if (bodyHasUnknownEncoding(response.headers)) {
            logger.print("<-- END HTTP (encoded body omitted)")
            return response
        }

        processResponseBody(response, contentLength)

        return response
    }

    private fun printResponseMetadata(response: Response, contentLength: Long, tookMs: Long) {
        val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"
        val message = buildString {
            append("<-- ")
            append(response.code)
            if (response.message.isNotEmpty()) {
                append(" ")
                append(response.message)
            }
            append(" ")
            append(response.request.url)
            append(" (")
            append(tookMs)
            append("ms, ")
            append(bodySize)
            append(" body)")
        }
        logger.print(message)
    }

    private fun printResponseHeaders(headers: Headers) {
        for (i in 0 until headers.size) {
            logger.print("${headers.name(i)}: ${headers.value(i)}")
        }
    }

    private fun processResponseBody(response: Response, contentLength: Long) {
        if (contentLength == 0L) {
            logger.print("<-- END HTTP (No response body)")
            return
        }

        val responseBody = response.body
        if (responseBody == null) {
            logger.print("<-- END HTTP (No response body)")
            return
        }

        val responseClone = response.newBuilder().build()
        val source = responseClone.body?.source()
        if (source == null) {
            logger.print("<-- END HTTP (No response body source)")
            return
        }

        try {
            // Buffer only up to maxBodySize
            source.request(maxBodySize)
            val buffer = source.buffer.clone()

            if (!isPlaintext(buffer)) {
                logger.print("<-- END HTTP (binary ${buffer.size}-byte body omitted)")
                return
            }

            val contentType = responseBody.contentType()
            val charset = contentType?.charset(UTF8) ?: UTF8

            // Process gzip if needed
            val (processedBuffer, gzippedLength) = processGzipEncodingIfNeeded(
                response.headers,
                buffer
            )

            printResponseBodyContent(processedBuffer, contentLength, charset, gzippedLength)
        } catch (e: Exception) {
            logger.print("<-- ERROR reading response body: ${e.message}")
        }
    }

    private fun processGzipEncodingIfNeeded(headers: Headers, buffer: Buffer): Pair<Buffer, Long?> {
        if ("gzip".equals(headers["Content-Encoding"], ignoreCase = true)) {
            val gzippedLength = buffer.size
            val uncompressedBuffer = Buffer()

            GzipSource(buffer.clone()).use { gzippedResponseBody ->
                uncompressedBuffer.writeAll(gzippedResponseBody)
            }

            return Pair(uncompressedBuffer, gzippedLength)
        }

        return Pair(buffer, null)
    }

    private fun printResponseBodyContent(
        buffer: Buffer,
        contentLength: Long,
        charset: Charset,
        gzippedLength: Long?,
    ) {
        if (contentLength > maxBodySize) {
            logger.print("<-- BODY TOO LARGE (${contentLength} bytes), showing first $maxBodySize bytes:")
        }

        logger.print("")
        if (buffer.size > 0) {
            logger.print(buffer.readString(charset))
        }

        val endMessage = if (gzippedLength != null) {
            "<-- END HTTP (${buffer.size}-byte, ${gzippedLength}-gzipped-byte body)"
        } else {
            "<-- END HTTP (${buffer.size}-byte body)"
        }
        logger.print(endMessage)
    }

    @Suppress("MagicNumber")
    private fun isPlaintext(buffer: Buffer): Boolean {
        return try {
            val prefix = Buffer()
            val byteCount = minOf(buffer.size, 64)
            buffer.copyTo(prefix, 0, byteCount)

            // Check if there are any non-whitespace control characters
            !prefix.snapshot().utf8().take(16).any { codePoint ->
                Character.isISOControl(codePoint.code) && !Character.isWhitespace(codePoint.code)
            }
        } catch (_: EOFException) {
            false // Truncated UTF-8 sequence
        } catch (_: Exception) {
            false // Any other issue indicates non-plaintext
        }
    }

    private fun bodyHasUnknownEncoding(headers: Headers): Boolean {
        val contentEncoding = headers["Content-Encoding"] ?: return false
        return !contentEncoding.equals("identity", ignoreCase = true) &&
                !contentEncoding.equals("gzip", ignoreCase = true)
    }

    private interface Logger {
        fun print(message: String)
    }
}