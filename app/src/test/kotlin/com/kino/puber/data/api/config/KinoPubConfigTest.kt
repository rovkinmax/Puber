package com.kino.puber.data.api.config

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class KinoPubConfigTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun mockAndroidBase64() {
            mockkStatic(Base64::class)
            every { Base64.decode(any<ByteArray>(), any()) } returns "kinopub.test".toByteArray()
        }

        @JvmStatic
        @AfterAll
        fun restoreAndroidBase64() {
            unmockkStatic(Base64::class)
        }
    }

    @AfterEach
    fun resetConfig() {
        KinoPubConfig.setDomainOverride(null)
    }

    @Test
    fun pinnedEndpoint_takesPrecedenceOverPersistedDomainOverride() {
        val customEndpoint = KinoPubConfig.BUILT_IN_ENDPOINTS.first()
        val pinnedEndpoint = ApiEndpointPreset(
            domain = "127.0.0.1:18765",
            apiHost = "127.0.0.1",
            mainBaseUrl = "http://127.0.0.1:18765/v1/",
            oauthBaseUrl = "http://127.0.0.1:18765/oauth2/",
            extraBaseUrl = "http://127.0.0.1:18765/",
        )

        KinoPubConfig.setDomainOverride(customEndpoint.domain)
        KinoPubConfig.setPinnedEndpoint(pinnedEndpoint)

        assertEquals(ApiEndpointMode.PINNED, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        assertTrue(KinoPubConfig.IS_PINNED_ENDPOINT)
        assertEquals(pinnedEndpoint, KinoPubConfig.CURRENT_ENDPOINT)
        assertEquals(pinnedEndpoint.mainBaseUrl, KinoPubConfig.MAIN_API_BASE_URL)
        assertEquals(pinnedEndpoint.oauthBaseUrl, KinoPubConfig.OAUTH_BASE_URL)
        assertEquals(pinnedEndpoint.extraBaseUrl, KinoPubConfig.EXTRA_API_BASE_URL)
    }

    @Test
    fun clearingPinnedEndpoint_restoresDefaultWithoutChangingProductionPreset() {
        val defaultEndpoint = KinoPubConfig.BUILT_IN_ENDPOINTS.first()
        val pinnedEndpoint = defaultEndpoint.copy(
            domain = "127.0.0.1:18765",
            apiHost = "127.0.0.1",
            mainBaseUrl = "http://127.0.0.1:18765/v1/",
            oauthBaseUrl = "http://127.0.0.1:18765/oauth2/",
            extraBaseUrl = "http://127.0.0.1:18765/",
        )

        KinoPubConfig.setPinnedEndpoint(pinnedEndpoint)
        KinoPubConfig.clearPinnedEndpoint()

        assertEquals(ApiEndpointMode.DEFAULT, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        assertFalse(KinoPubConfig.IS_PINNED_ENDPOINT)
        assertEquals(defaultEndpoint, KinoPubConfig.CURRENT_ENDPOINT)
    }

    @Test
    fun customDomain_keepsExistingProductionEndpointResolution() {
        val customDomain = "mirror.example.test"

        KinoPubConfig.setDomainOverride(customDomain)

        assertEquals(ApiEndpointMode.CUSTOM, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        assertFalse(KinoPubConfig.IS_PINNED_ENDPOINT)
        assertEquals(customDomain, KinoPubConfig.CURRENT_API_DOMAIN)
        assertEquals("https://$customDomain/v1/", KinoPubConfig.MAIN_API_BASE_URL)
        assertEquals(
            "https://api.${KinoPubConfig.DEFAULT_API_DOMAIN}/oauth2/",
            KinoPubConfig.OAUTH_BASE_URL,
        )
    }
}
