package com.kino.puber.domain.interactor.api

import android.util.Log
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.config.ApiEndpointMode
import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.genre.GenreInteractor
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ApiDomainInteractorPinnedEndpointTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun mockAndroidLogging() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
        }

        @JvmStatic
        @AfterAll
        fun restoreAndroidLogging() {
            unmockkStatic(Log::class)
        }
    }

    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private val api = mockk<KinoPubApiClient>()
    private val interactor = ApiDomainInteractor(
        preferences = preferences,
        itemDetailsRepository = ItemDetailsRepository(api),
        genreInteractor = GenreInteractor(api),
        okHttpClient = OkHttpClient(),
    )

    @BeforeEach
    fun pinLoopbackEndpoint() {
        KinoPubConfig.setPinnedEndpoint(
            ApiEndpointPreset(
                domain = "127.0.0.1:18765",
                apiHost = "127.0.0.1",
                mainBaseUrl = "http://127.0.0.1:18765/v1/",
                oauthBaseUrl = "http://127.0.0.1:18765/oauth2/",
                extraBaseUrl = "http://127.0.0.1:18765/",
            ),
        )
    }

    @AfterEach
    fun resetConfig() {
        KinoPubConfig.setDomainOverride(null)
    }

    @Test
    fun initialize_doesNotReadOrApplyPersistedProductionDomain() {
        every { preferences.getApiDomain() } returns "api.production.example"

        interactor.initialize()

        assertEquals(ApiEndpointMode.PINNED, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        assertEquals("127.0.0.1:18765", KinoPubConfig.CURRENT_API_DOMAIN)
        verify(exactly = 0) { preferences.getApiDomain() }
    }

    @Test
    fun autoResolve_returnsPinnedEndpointWithoutProbingFallbacks() = runTest {
        val result = interactor.autoResolveWorkingDomain()

        assertEquals(
            ApiDomainAutoResolveResult.Success(
                state = ApiDomainState("127.0.0.1:18765", "127.0.0.1:18765"),
                changed = false,
            ),
            result,
        )
        assertFalse(result is ApiDomainAutoResolveResult.NotFound)
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { preferences.saveApiDomain(any()) }
    }

    @Test
    fun resetToDefault_keepsPinnedEndpoint() {
        val state = interactor.resetToDefault()

        assertEquals("127.0.0.1:18765", state.domain)
        assertEquals("127.0.0.1:18765", state.customDomain)
        assertEquals(ApiEndpointMode.PINNED, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        verify(exactly = 0) { preferences.saveApiDomain(any()) }
    }

    @Test
    fun saveCustomDomain_keepsPinnedEndpointWithoutPersistingInput() {
        val result = interactor.saveCustomDomain("api.production.example")

        assertEquals(
            ApiDomainUpdateResult.Success(
                ApiDomainState("127.0.0.1:18765", "127.0.0.1:18765"),
            ),
            result,
        )
        assertEquals(ApiEndpointMode.PINNED, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        verify(exactly = 0) { preferences.saveApiDomain(any()) }
    }
}
