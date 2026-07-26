package com.kino.puber.data.api.network

import com.kino.puber.core.logger.SensitiveRequestLogPolicy

internal class CurlLogFormatter(
    private val policy: SensitiveRequestLogPolicy = SensitiveRequestLogPolicy(),
) {
    fun formatRequest(
        method: String,
        url: String,
        headers: List<Pair<String, String>>,
        textBody: String?,
    ): List<String> {
        val safeUrl = policy.sanitizeUrl(url)
        val curl = buildString {
            append("curl -X ").append(shellQuote(method))
            headers.forEach { (name, value) ->
                val safeValue = policy.sanitizeHeader(name, value)
                append(" -H ")
                    .append(shellQuote("$name: $safeValue"))
            }
            if (textBody != null) {
                val safeBody = policy.sanitizeRequestBody(url, textBody)
                append(" --data-raw ")
                    .append(shellQuote(safeBody))
            } else {
                append(" --data-binary ")
                    .append(shellQuote("<non-text or unknown body>"))
            }
            append(" -- ")
                .append(shellQuote(safeUrl))
        }
        return listOf(
            "╭--- cURL ($safeUrl)",
            curl,
            "╰--- (copy & paste to terminal)",
        )
    }

    fun formatResponse(
        status: String,
        url: String,
        headers: List<Pair<String, String>>,
        body: String,
    ): List<String> {
        val safeUrl = policy.sanitizeUrl(url)
        val safeBody = policy.sanitizeResponseBody(url, body)
        return buildList {
            add("<-- $status $safeUrl")
            headers.forEach { (name, value) ->
                add("$name: ${policy.sanitizeHeader(name, value)}")
            }
            add("")
            add(safeBody)
            add("<-- END HTTP (${safeBody.length}-char body)")
        }
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}
