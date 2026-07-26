package com.kino.puber.data.api.network

import com.kino.puber.core.logger.REDACTED_LOG_BODY
import com.kino.puber.core.logger.REDACTED_LOG_VALUE
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class CurlLogFormatterTest {

    private val formatter = CurlLogFormatter()

    @Test
    fun requestFormattingRedactsSensitiveUrlHeadersAndTextBody() {
        val lines = formatter.formatRequest(
            method = "POST",
            url = "https://example.test/v1/history?page=page-secret&id=history-secret",
            headers = listOf(
                "Authorization" to "Bearer access-secret",
                "X-Request-Id" to "request-diagnostic",
                "User-Agent" to "Puber/account-user/android-id-secret",
            ),
            textBody = """{"title":"Private title","refresh_token":"refresh-secret"}""",
        )
        val output = lines.joinToString("\n")

        assertEquals(3, lines.size)
        assertNoSecrets(
            output,
            "page-secret",
            "history-secret",
            "access-secret",
            "Private title",
            "refresh-secret",
            "account-user",
            "android-id-secret",
        )
        assertTrue(output.contains("curl -X 'POST'"))
        assertTrue(output.contains("/v1/history"))
        assertTrue(output.contains("Authorization: $REDACTED_LOG_VALUE"))
        assertTrue(output.contains("X-Request-Id: request-diagnostic"))
        assertTrue(output.contains("User-Agent: $REDACTED_LOG_VALUE"))
        assertTrue(output.contains(REDACTED_LOG_BODY))
    }

    @Test
    fun responseFormattingRedactsUrlHeadersBodyAndUsesSanitizedLength() {
        val lines = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/v1/watching/marktime?id=item-secret&time=time-secret",
            headers = listOf(
                "Set-Cookie" to "access_token=access-secret",
                "X-Trace" to "trace-diagnostic",
            ),
            body = """{"title":"Private title","id":"history-secret"}""",
        )
        val output = lines.joinToString("\n")

        assertEquals(6, lines.size)
        assertNoSecrets(
            output,
            "item-secret",
            "time-secret",
            "access-secret",
            "Private title",
            "history-secret",
        )
        assertTrue(output.contains("<-- 200 OK"))
        assertTrue(output.contains("Set-Cookie: $REDACTED_LOG_VALUE"))
        assertTrue(output.contains("X-Trace: trace-diagnostic"))
        assertTrue(output.contains(REDACTED_LOG_BODY))
        assertTrue(output.contains("(${REDACTED_LOG_BODY.length}-char body)"))
    }

    @Test
    fun authenticationFormattingNeverEmitsOAuthSecrets() {
        val request = formatter.formatRequest(
            method = "POST",
            url = "https://example.test/oauth2/device?client_secret=client-secret&refresh_token=refresh-secret",
            headers = emptyList(),
            textBody = """{"code":"device-secret"}""",
        )
        val response = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/oauth2/device",
            headers = emptyList(),
            body = """{"access_token":"access-secret","refresh_token":"new-refresh-secret"}""",
        )
        val output = (request + response).joinToString("\n")

        assertNoSecrets(
            output,
            "client-secret",
            "refresh-secret",
            "device-secret",
            "access-secret",
            "new-refresh-secret",
        )
        assertTrue(output.contains(REDACTED_LOG_BODY))
    }

    @Test
    fun accountAndDeviceResponseFormattingSuppressesPersonalBodies() {
        val accountResponse = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/v1/user",
            headers = listOf("Content-Type" to "application/json"),
            body = """{"username":"account-user","email":"private@example.test"}""",
        )
        val deviceResponse = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/v1/device/devices",
            headers = listOf("Content-Type" to "application/json"),
            body = """
                {
                  "title":"Living room",
                  "hardware":"private-hardware",
                  "software":"private-software",
                  "last_seen":"private-time"
                }
            """.trimIndent(),
        )
        val output = (accountResponse + deviceResponse).joinToString("\n")

        assertNoSecrets(
            output,
            "account-user",
            "private@example.test",
            "Living room",
            "private-hardware",
            "private-software",
            "private-time",
        )
        assertEquals(2, output.lines().count { it == REDACTED_LOG_BODY })
        assertTrue(output.contains("/v1/user"))
        assertTrue(output.contains("/v1/device/devices"))
    }

    @Test
    fun watchingResponseFormattingSuppressesAccountViewingPayloads() {
        val routes = listOf(
            "https://example.test/v1/watching/movie?subscribed=account-subscription-secret",
            "https://example.test/v1/watching/serials?subscribed=account-subscription-secret",
        )
        val output = routes.flatMap { url ->
            formatter.formatResponse(
                status = "200 OK",
                url = url,
                headers = listOf("Content-Type" to "application/json"),
                body = """
                    {
                      "items":[{
                        "id":"private-item-id",
                        "title":"Private watched title",
                        "watching":{"time":"private-watching-time"}
                      }]
                    }
                """.trimIndent(),
            )
        }.joinToString("\n")

        assertNoSecrets(
            output,
            "account-subscription-secret",
            "private-item-id",
            "Private watched title",
            "private-watching-time",
        )
        assertEquals(2, output.lines().count { it == REDACTED_LOG_BODY })
        assertTrue(output.contains("/v1/watching/movie"))
        assertTrue(output.contains("/v1/watching/serials"))
    }

    @Test
    fun requestAndResponseFormattingRedactFragmentAndUrlValuedHeaderCredentials() {
        val request = formatter.formatRequest(
            method = "GET",
            url = "https://client.test/callback#access_token=request-access-secret&state=request-state",
            headers = listOf(
                "Link" to "<https://request-user:request-password@api.test/next" +
                    "?page=2&refresh%5Ftoken=request-refresh-secret" +
                    "#access%5Ftoken=request-link-access-secret>; rel=\"next\"",
            ),
            textBody = null,
        )
        val response = formatter.formatResponse(
            status = "302 Found",
            url = "https://example.test/v1/items?page=3" +
                "#tab=history&refresh_token=response-url-refresh-secret",
            headers = listOf(
                "Location" to "https://response-user:response-password@client.test/callback" +
                    "?refresh%5Ftoken=response-location-refresh-secret&state=response-state" +
                    "#access%5Ftoken=response-location-access-secret",
            ),
            body = """{"count":2}""",
        )
        val output = (request + response).joinToString("\n")

        assertNoSecrets(
            output,
            "request-access-secret",
            "request-user",
            "request-password",
            "request-refresh-secret",
            "request-link-access-secret",
            "response-url-refresh-secret",
            "response-user",
            "response-password",
            "response-location-refresh-secret",
            "response-location-access-secret",
        )
        assertTrue(output.contains("state=request-state"))
        assertTrue(output.contains("page=2"))
        assertTrue(output.contains("page=3"))
        assertTrue(output.contains("tab=history"))
        assertTrue(output.contains("state=response-state"))
        assertTrue(output.contains("access%5Ftoken=$REDACTED_LOG_VALUE"))
        assertTrue(output.contains("refresh%5Ftoken=$REDACTED_LOG_VALUE"))
    }

    @Test
    fun copyPasteCommandPreservesHostileArgumentsWithoutExecutingThem(
        @TempDir tempDir: Path,
    ) {
        val commandSubstitutionMarker = tempDir.resolve("command-substitution-ran")
        val backtickMarker = tempDir.resolve("backtick-ran")
        val hostileValue = buildString {
            append("\$HOME ")
            append("\$(touch '").append(commandSubstitutionMarker).append("') ")
            append("`touch '").append(backtickMarker).append("'` ")
            append("'single' \"double\" \\\\backslash")
        }
        val url = "https://example.test/v1/items?diagnostic=$hostileValue"
        val body = "@body=$hostileValue"
        val command = formatter.formatRequest(
            method = "POST",
            url = url,
            headers = listOf("X-Hostile" to hostileValue),
            textBody = body,
        )[1]
        val capturedArguments = executeWithFakeCurl(
            command = command,
            tempDir = tempDir,
        )

        assertEquals(
            listOf(
                "-X",
                "POST",
                "-H",
                "X-Hostile: $hostileValue",
                "--data-raw",
                body,
                "--",
                url,
            ),
            capturedArguments,
        )
        assertFalse(Files.exists(commandSubstitutionMarker))
        assertFalse(Files.exists(backtickMarker))
    }

    @Test
    fun historyToExactPlayerFlowNeverEmitsViewingOrCredentialValues() {
        val historyResponse = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/v1/history?page=history-page-secret",
            headers = listOf("Authorization" to "Bearer history-token-secret"),
            body = """
                {
                  "id":"history-id-secret",
                  "item":{"id":4242,"title":"Watched title secret"},
                  "video":{"id":"video-id-secret","watching":{"time":"watch-time-secret","duration":"duration-secret"}}
                }
            """.trimIndent(),
        )
        val detailsRequest = formatter.formatRequest(
            method = "GET",
            url = "https://example.test/v1/items/4242",
            headers = listOf("Authorization" to "Bearer player-token-secret"),
            textBody = null,
        )
        val detailsResponse = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/v1/items/4242",
            headers = listOf("Set-Cookie" to "refresh_token=refresh-token-secret"),
            body = """
                {
                  "title":"Watched title secret",
                  "videos":[
                    {"id":"video-id-secret","watching":{"time":"watch-time-secret","duration":"duration-secret"}}
                  ]
                }
            """.trimIndent(),
        )
        val marktimeRequest = formatter.formatRequest(
            method = "GET",
            url = "https://example.test/v1/watching/marktime?id=video-id-secret&time=marktime-secret",
            headers = listOf("Authorization" to "Bearer marktime-token-secret"),
            textBody = null,
        )
        val output = (historyResponse + detailsRequest + detailsResponse + marktimeRequest).joinToString("\n")

        assertNoSecrets(
            output,
            "history-page-secret",
            "history-token-secret",
            "history-id-secret",
            "4242",
            "Watched title secret",
            "video-id-secret",
            "watch-time-secret",
            "duration-secret",
            "player-token-secret",
            "refresh-token-secret",
            "marktime-secret",
            "marktime-token-secret",
        )
        assertTrue(output.contains("/v1/history"))
        assertTrue(output.contains("/v1/items/$REDACTED_LOG_VALUE"))
        assertTrue(output.contains("/v1/watching/marktime"))
        assertTrue(output.contains(REDACTED_LOG_BODY))
    }

    @Test
    fun normalRequestAndResponseKeepUsefulDiagnostics() {
        val request = formatter.formatRequest(
            method = "GET",
            url = "https://example.test/v1/items?page=3",
            headers = listOf("Accept" to "application/json"),
            textBody = null,
        )
        val response = formatter.formatResponse(
            status = "200 OK",
            url = "https://example.test/v1/items?page=3",
            headers = listOf("Content-Type" to "application/json"),
            body = """{"count":2}""",
        )
        val output = (request + response).joinToString("\n")

        assertTrue(output.contains("https://example.test/v1/items?page=3"))
        assertTrue(output.contains("Accept: application/json"))
        assertTrue(output.contains("<non-text or unknown body>"))
        assertTrue(output.contains("Content-Type: application/json"))
        assertTrue(output.contains("""{"count":2}"""))
        assertFalse(output.contains(REDACTED_LOG_BODY))
    }

    private fun assertNoSecrets(output: String, vararg secrets: String) {
        secrets.forEach { secret ->
            assertFalse(output.contains(secret), "Leaked secret: $secret")
        }
    }

    private fun executeWithFakeCurl(
        command: String,
        tempDir: Path,
    ): List<String> {
        val curlScript = tempDir.resolve("curl")
        val capturedArguments = tempDir.resolve("curl-arguments")
        Files.write(
            curlScript,
            """
                #!/bin/sh
                : > "${'$'}CURL_CAPTURE_FILE"
                for argument in "${'$'}@"; do
                  printf '%s\0' "${'$'}argument" >> "${'$'}CURL_CAPTURE_FILE"
                done
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        )
        assertTrue(curlScript.toFile().setExecutable(true))

        val process = ProcessBuilder("sh", "-c", command)
            .apply {
                environment()["CURL_CAPTURE_FILE"] = capturedArguments.toString()
                environment()["PATH"] = listOf(
                    tempDir.toString(),
                    System.getenv("PATH").orEmpty(),
                ).joinToString(File.pathSeparator)
            }
            .start()
        val errorOutput = process.errorStream
            .bufferedReader()
            .use { it.readText() }

        assertEquals(0, process.waitFor(), errorOutput)
        return Files.readAllBytes(capturedArguments)
            .toString(StandardCharsets.UTF_8)
            .split('\u0000')
            .filter(String::isNotEmpty)
    }
}
