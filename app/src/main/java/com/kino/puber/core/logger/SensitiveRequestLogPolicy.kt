package com.kino.puber.core.logger

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal const val REDACTED_LOG_VALUE = "<redacted>"
internal const val REDACTED_LOG_BODY = "<redacted sensitive body>"

internal enum class SensitiveRequestKind(
    val redactAllQueryValues: Boolean,
    val redactEntireBody: Boolean,
) {
    Standard(
        redactAllQueryValues = false,
        redactEntireBody = false,
    ),
    History(
        redactAllQueryValues = true,
        redactEntireBody = true,
    ),
    ItemDetails(
        redactAllQueryValues = true,
        redactEntireBody = true,
    ),
    PlaybackProgress(
        redactAllQueryValues = true,
        redactEntireBody = true,
    ),
    Authentication(
        redactAllQueryValues = true,
        redactEntireBody = true,
    ),
    AccountOrDevice(
        redactAllQueryValues = true,
        redactEntireBody = true,
    ),
}

internal class SensitiveRequestLogPolicy {

    fun classify(url: String): SensitiveRequestKind {
        val path = extractPath(url).trimEnd('/').lowercase()
        val segments = path.split('/').filter(String::isNotBlank)
        return when {
            "history" in segments -> SensitiveRequestKind.History
            segments.isItemDetailsPath() -> SensitiveRequestKind.ItemDetails
            "watching" in segments -> SensitiveRequestKind.PlaybackProgress
            "oauth2" in segments -> SensitiveRequestKind.Authentication
            "user" in segments ||
                "device" in segments ||
                "devices" in segments -> SensitiveRequestKind.AccountOrDevice
            else -> SensitiveRequestKind.Standard
        }
    }

    fun sanitizeUrl(url: String): String {
        val credentialSafeUrl = USER_INFO_REGEX.replace(url) { match ->
            "${match.groupValues[1]}$REDACTED_LOG_VALUE@"
        }
        val fragmentStart = credentialSafeUrl.indexOf('#')
        val urlWithoutFragment = if (fragmentStart >= 0) {
            credentialSafeUrl.substring(0, fragmentStart)
        } else {
            credentialSafeUrl
        }
        val fragment = fragmentStart
            .takeIf { it >= 0 }
            ?.let { credentialSafeUrl.substring(it + 1) }
        val queryStart = urlWithoutFragment.indexOf('?')
        val kind = classify(credentialSafeUrl)
        val sanitizedBase = if (queryStart >= 0) {
            val query = urlWithoutFragment.substring(queryStart + 1)
            buildString {
                append(urlWithoutFragment.substring(0, queryStart + 1))
                append(sanitizeParameterSection(query, kind.redactAllQueryValues))
            }
        } else {
            urlWithoutFragment
        }
        val sanitizedUrl = if (fragment != null) {
            "$sanitizedBase#${sanitizeParameterSection(fragment, kind.redactAllQueryValues)}"
        } else {
            sanitizedBase
        }
        return redactCredentialFields(sanitizePathIdentity(sanitizedUrl, kind))
    }

    fun sanitizeHeader(name: String, value: String): String {
        return when (name.lowercase()) {
            in SENSITIVE_HEADER_NAMES -> REDACTED_LOG_VALUE
            in DIRECT_URL_HEADER_NAMES -> sanitizeUrl(value)
            "link" -> sanitizeLinkHeader(value)
            else -> redactCredentialFields(value)
        }
    }

    fun sanitizeRequestBody(url: String, body: String): String {
        return sanitizeBody(url, body)
    }

    fun sanitizeResponseBody(url: String, body: String): String {
        return sanitizeBody(url, body)
    }

    fun sanitizeText(value: String): String {
        val sanitizedUrls = URL_REGEX.replace(value) { match ->
            sanitizeUrl(match.value)
        }
        val sanitizedPaths = ITEM_DETAILS_TEXT_ID_REGEX.replace(sanitizedUrls) { match ->
            match.groupValues[1] + REDACTED_LOG_VALUE
        }
        return redactCredentialFields(sanitizedPaths)
    }

    fun sanitizeThrowable(throwable: Throwable): Throwable {
        return SanitizedLogThrowable(
            sourceType = throwable::class.qualifiedName ?: throwable.javaClass.name,
            sourceMessage = throwable.message?.let(::sanitizeText),
            sourceStackTrace = throwable.stackTrace,
            sourceCause = throwable.cause?.let(::sanitizeThrowable),
            sourceSuppressed = throwable.suppressed.map(::sanitizeThrowable),
        )
    }

    private fun sanitizeBody(url: String, body: String): String {
        return if (classify(url).redactEntireBody) {
            REDACTED_LOG_BODY
        } else {
            redactCredentialFields(body)
        }
    }

    private fun sanitizeQueryParameter(
        parameter: String,
        redactAllValues: Boolean,
    ): String {
        if (parameter.isEmpty()) return parameter
        val separatorIndex = parameter.indexOf('=')
        val rawName = if (separatorIndex >= 0) {
            parameter.substring(0, separatorIndex)
        } else {
            parameter
        }
        val decodedName = runCatching {
            URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawName)
        val shouldRedact = redactAllValues || decodedName.lowercase() in SENSITIVE_PARAMETER_NAMES
        return if (shouldRedact) {
            "$rawName=$REDACTED_LOG_VALUE"
        } else {
            parameter
        }
    }

    private fun sanitizeParameterSection(
        section: String,
        redactAllValues: Boolean,
    ): String {
        return section
            .split('&')
            .joinToString("&") { parameter ->
                sanitizeQueryParameter(
                    parameter = parameter,
                    redactAllValues = redactAllValues,
                )
            }
    }

    private fun sanitizeLinkHeader(value: String): String {
        val sanitizedTargets = LINK_TARGET_REGEX.replace(value) { match ->
            "<${sanitizeUrl(match.groupValues[1])}>"
        }
        return redactCredentialFields(sanitizedTargets)
    }

    private fun sanitizePathIdentity(
        url: String,
        kind: SensitiveRequestKind,
    ): String {
        return if (kind == SensitiveRequestKind.ItemDetails) {
            ITEM_DETAILS_ID_REGEX.replace(url) { match ->
                match.groupValues[1] + REDACTED_LOG_VALUE
            }
        } else {
            url
        }
    }

    private fun redactCredentialFields(value: String): String {
        val jsonRedacted = JSON_CREDENTIAL_REGEX.replace(value) { match ->
            match.groupValues[1] + REDACTED_LOG_VALUE + match.groupValues[3]
        }
        val formRedacted = FORM_CREDENTIAL_REGEX.replace(jsonRedacted) { match ->
            match.groupValues[1] + REDACTED_LOG_VALUE
        }
        return BEARER_TOKEN_REGEX.replace(formRedacted) {
            "Bearer $REDACTED_LOG_VALUE"
        }
    }

    private fun extractPath(url: String): String {
        return runCatching { URI(url).path.orEmpty() }
            .getOrElse { url.substringBefore('?').substringBefore('#') }
    }

    private fun List<String>.isItemDetailsPath(): Boolean {
        val itemsIndex = indexOfLast { it == "items" }
        return itemsIndex >= 0 &&
            itemsIndex == lastIndex - 1 &&
            last().toIntOrNull() != null
    }

    private companion object {
        val SENSITIVE_HEADER_NAMES = setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "user-agent",
            "x-api-key",
        )
        val DIRECT_URL_HEADER_NAMES = setOf(
            "content-location",
            "location",
            "referer",
            "referrer",
        )
        val SENSITIVE_PARAMETER_NAMES = setOf(
            "access_token",
            "refresh_token",
            "client_secret",
            "device_token",
            "code",
        )
        val USER_INFO_REGEX = Regex(
            """(?i)(((?:[a-z][a-z0-9+.-]*:)?//))[^/@\s?#]+@""",
        )
        val LINK_TARGET_REGEX = Regex("""<([^>]*)>""")
        val ITEM_DETAILS_ID_REGEX = Regex("""(?i)(/items/)\d+(?=/?(?:[?#]|$))""")
        val ITEM_DETAILS_TEXT_ID_REGEX = Regex(
            """(?i)(/items/)\d+(?=/?(?:[?#\s,\]}):]|$))""",
        )
        val URL_REGEX = Regex("""(?i)\bhttps?://[^\s\[\]()<>"',;]+""")
        val JSON_CREDENTIAL_REGEX = Regex(
            """(?i)("(?:access_token|refresh_token|client_secret|device_token|code)"\s*:\s*")([^"]*)(")""",
        )
        val FORM_CREDENTIAL_REGEX = Regex(
            pattern = """(?i)((?:^|[?&#\s])""" +
                """(?:access_token|refresh_token|client_secret|device_token|code)=)""" +
                """(?!<redacted>)([^&#\s,;>"']+)""",
        )
        val BEARER_TOKEN_REGEX = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    }
}

private class SanitizedLogThrowable(
    sourceType: String,
    sourceMessage: String?,
    sourceStackTrace: Array<StackTraceElement>,
    sourceCause: Throwable?,
    sourceSuppressed: List<Throwable>,
) : Throwable(
    message = listOfNotNull(sourceType, sourceMessage?.takeIf(String::isNotBlank))
        .joinToString(": "),
    cause = sourceCause,
) {
    init {
        stackTrace = sourceStackTrace
        sourceSuppressed.forEach(::addSuppressed)
    }
}
