package com.kino.puber.data.api.network

import com.kino.puber.core.logger.REDACTED_LOG_BODY
import com.kino.puber.core.logger.REDACTED_LOG_VALUE
import com.kino.puber.core.logger.SensitiveRequestKind
import com.kino.puber.core.logger.SensitiveRequestLogPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SensitiveRequestLogPolicyTest {

    private val policy = SensitiveRequestLogPolicy()

    @Test
    fun classifyRecognizesSensitiveApiPaths() {
        assertEquals(
            SensitiveRequestKind.History,
            policy.classify("https://example.test/v1/history?page=page-secret"),
        )
        assertEquals(
            SensitiveRequestKind.History,
            policy.classify("https://example.test/api/v1/history/clear-for-media?id=media-secret"),
        )
        assertEquals(
            SensitiveRequestKind.ItemDetails,
            policy.classify("https://example.test/v1/items/4242"),
        )
        assertEquals(
            SensitiveRequestKind.ItemDetails,
            policy.classify("https://example.test/api/v1/items/4242/"),
        )
        assertEquals(
            SensitiveRequestKind.PlaybackProgress,
            policy.classify("https://example.test/v1/watching/marktime?id=item-secret"),
        )
        assertEquals(
            SensitiveRequestKind.PlaybackProgress,
            policy.classify("https://example.test/v1/watching/toggle?id=item-secret"),
        )
        assertEquals(
            SensitiveRequestKind.PlaybackProgress,
            policy.classify("https://example.test/v1/watching/movie?subscribed=account-secret"),
        )
        assertEquals(
            SensitiveRequestKind.PlaybackProgress,
            policy.classify("https://example.test/v1/watching/serials?subscribed=account-secret"),
        )
        assertEquals(
            SensitiveRequestKind.PlaybackProgress,
            policy.classify("https://example.test/v1/watching/togglewatchlist?id=item-secret"),
        )
        assertEquals(
            SensitiveRequestKind.Authentication,
            policy.classify("https://example.test/oauth2/device?code=device-secret"),
        )
        assertEquals(
            SensitiveRequestKind.AccountOrDevice,
            policy.classify("https://example.test/v1/user"),
        )
        assertEquals(
            SensitiveRequestKind.AccountOrDevice,
            policy.classify("https://example.test/v1/device/devices"),
        )
        assertEquals(
            SensitiveRequestKind.Standard,
            policy.classify("https://example.test/v1/items/search?q=title"),
        )
    }

    @Test
    fun sensitiveEndpointsRedactEveryQueryValue() {
        val urls = listOf(
            "https://example.test/v1/history?page=page-secret&id=history-secret",
            "https://example.test/v1/watching/marktime?id=item-secret&time=time-secret",
            "https://example.test/v1/watching/toggle?id=item-secret&status=status-secret",
            "https://example.test/v1/watching/movie?subscribed=account-secret",
            "https://example.test/v1/watching/serials?subscribed=account-secret",
        )

        urls.forEach { url ->
            val sanitized = policy.sanitizeUrl(url)

            assertFalse(sanitized.contains("secret"))
            assertTrue(sanitized.contains("=$REDACTED_LOG_VALUE"))
        }
    }

    @Test
    fun itemDetailsUrlsRedactMediaIdentityButKeepRouteShape() {
        assertEquals(
            "https://example.test/api/v1/items/$REDACTED_LOG_VALUE/" +
                "?diagnostic=$REDACTED_LOG_VALUE",
            policy.sanitizeUrl(
                "https://example.test/api/v1/items/4242/?diagnostic=request-value",
            ),
        )
    }

    @Test
    fun standardUrlsRedactCredentialParametersButKeepUsefulValues() {
        val sanitized = policy.sanitizeUrl(
            "https://example.test/v1/items?page=3&refresh_token=refresh-secret",
        )

        assertTrue(sanitized.contains("page=3"))
        assertTrue(sanitized.contains("refresh_token=$REDACTED_LOG_VALUE"))
        assertFalse(sanitized.contains("refresh-secret"))
    }

    @Test
    fun urlFragmentsRedactCredentialParametersAndKeepNonSensitiveComponents() {
        val fragmentOnly = policy.sanitizeUrl(
            "https://client.test/callback#access_token=access-secret&state=state-diagnostic",
        )
        val queryAndFragment = policy.sanitizeUrl(
            "https://example.test/v1/items?page=3#tab=history&refresh_token=refresh-secret",
        )

        assertEquals(
            "https://client.test/callback#access_token=$REDACTED_LOG_VALUE&state=state-diagnostic",
            fragmentOnly,
        )
        assertEquals(
            "https://example.test/v1/items?page=3#tab=history&refresh_token=$REDACTED_LOG_VALUE",
            queryAndFragment,
        )
    }

    @Test
    fun sensitiveHeadersAndEmbeddedCredentialsAreRedacted() {
        assertEquals(
            REDACTED_LOG_VALUE,
            policy.sanitizeHeader("Authorization", "Bearer access-secret"),
        )
        assertEquals(
            REDACTED_LOG_VALUE,
            policy.sanitizeHeader("Set-Cookie", "refresh_token=refresh-secret"),
        )
        assertEquals(
            REDACTED_LOG_VALUE,
            policy.sanitizeHeader(
                "User-Agent",
                "Puber/account-user/android-id-secret",
            ),
        )
        assertEquals(
            "trace refresh_token=$REDACTED_LOG_VALUE",
            policy.sanitizeHeader("X-Debug", "trace refresh_token=refresh-secret"),
        )
        assertEquals(
            "https://client.test/callback?refresh_token=$REDACTED_LOG_VALUE" +
                "#access_token=$REDACTED_LOG_VALUE&state=state-diagnostic",
            policy.sanitizeHeader(
                "Location",
                "https://client.test/callback?refresh_token=refresh-secret" +
                    "#access_token=access-secret&state=state-diagnostic",
            ),
        )
        assertEquals(
            "<https://api.test/next?page=2&refresh_token=$REDACTED_LOG_VALUE" +
                "#access_token=$REDACTED_LOG_VALUE>; rel=\"next\"",
            policy.sanitizeHeader(
                "Link",
                "<https://api.test/next?page=2&refresh_token=refresh-secret" +
                    "#access_token=access-secret>; rel=\"next\"",
            ),
        )
    }

    @Test
    fun urlValuedHeadersRedactUserInfoAndEncodedCredentialNames() {
        assertEquals(
            "https://$REDACTED_LOG_VALUE@client.test/callback" +
                "?access%5Ftoken=$REDACTED_LOG_VALUE&state=location-diagnostic",
            policy.sanitizeHeader(
                "Location",
                "https://location-user:location-password@client.test/callback" +
                    "?access%5Ftoken=location-secret&state=location-diagnostic",
            ),
        )
        assertEquals(
            "<https://$REDACTED_LOG_VALUE@api.test/next" +
                "?page=2&refresh%5Ftoken=$REDACTED_LOG_VALUE>; rel=\"next\"",
            policy.sanitizeHeader(
                "Link",
                "<https://link-user:link-password@api.test/next" +
                    "?page=2&refresh%5Ftoken=link-secret>; rel=\"next\"",
            ),
        )
    }

    @Test
    fun historyItemDetailsPlaybackAndAuthenticationBodiesAreSuppressed() {
        val body = """{"title":"Private title","id":"history-secret"}"""
        val urls = listOf(
            "https://example.test/v1/history?page=1",
            "https://example.test/v1/items/4242",
            "https://example.test/v1/watching/marktime?id=1",
            "https://example.test/v1/watching/movie?subscribed=1",
            "https://example.test/v1/watching/serials?subscribed=1",
            "https://example.test/oauth2/device?code=secret",
        )

        urls.forEach { url ->
            assertEquals(REDACTED_LOG_BODY, policy.sanitizeRequestBody(url, body))
            assertEquals(REDACTED_LOG_BODY, policy.sanitizeResponseBody(url, body))
        }
    }

    @Test
    fun standardBodiesRedactTokenFieldsAndBearerValues() {
        val body = """
            {"access_token":"access-secret","refresh_token":"refresh-secret","note":"Bearer bearer-secret"}
        """.trimIndent()

        val sanitized = policy.sanitizeResponseBody(
            url = "https://example.test/v1/items",
            body = body,
        )

        assertFalse(sanitized.contains("access-secret"))
        assertFalse(sanitized.contains("refresh-secret"))
        assertFalse(sanitized.contains("bearer-secret"))
        assertTrue(sanitized.contains("access_token"))
        assertTrue(sanitized.contains(REDACTED_LOG_VALUE))
    }

    @Test
    fun accountAndDeviceResponseBodiesAreSuppressed() {
        val responses = mapOf(
            "https://example.test/v1/user" to
                """{"username":"account-user","email":"private@example.test"}""",
            "https://example.test/v1/device/devices" to
                """{"title":"Living room","hardware":"private-hardware","software":"private-software",""" +
                """"last_seen":"private-time"}""",
        )

        responses.forEach { (url, body) ->
            assertEquals(REDACTED_LOG_BODY, policy.sanitizeResponseBody(url, body))
        }
    }

    @Test
    fun throwableSanitizationPreservesCauseChainWithoutAuthenticationSecrets() {
        val refreshFailure = IllegalStateException(
            "Failed to refresh token",
            IllegalStateException(
                "POST https://example.test/oauth2/device?refresh_token=refresh-secret",
            ),
        )

        val sanitized = policy.sanitizeThrowable(refreshFailure)
        val output = sanitized.stackTraceToString()

        assertNotNull(sanitized.cause)
        assertTrue(output.contains("Failed to refresh token"))
        assertTrue(output.contains("refresh_token=$REDACTED_LOG_VALUE"))
        assertFalse(output.contains("refresh-secret"))
    }
}
