package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class KinoPubApiClientBookmarksTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpAndroidLogging() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
        }

        @JvmStatic
        @AfterAll
        fun tearDownAndroidLogging() {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun createBookmark_decodesFolderWrapperAndUsesFormParameters(
        @TempDir cacheDir: Path,
    ) = runTest {
        MockWebServerTestSupport().use { server ->
            server.route("/v1/bookmarks/create") {
                server.response(
                    status = 200,
                    body = """
                        {
                          "status": 200,
                          "folder": {
                            "id": 134,
                            "title": "Family",
                            "count": 0
                          }
                        }
                    """.trimIndent(),
                )
            }

            val result = client(cacheDir, server.url("/v1/")).createBookmark("Family")
            val request = server.takeRequest()

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertEquals(134, result.getOrThrow().id)
            assertEquals("Family", result.getOrThrow().title)
            assertEquals("POST", request.method)
            assertEquals("/v1/bookmarks/create", request.url.encodedPath)
            assertEquals("title=Family", requireNotNull(request.body).utf8())
            assertTrue(
                request.headers["Content-Type"]?.startsWith("application/x-www-form-urlencoded") == true,
            )
        }
    }

    @Test
    fun removeBookmarkItem_alwaysSendsExplicitFolder(
        @TempDir cacheDir: Path,
    ) = runTest {
        MockWebServerTestSupport().use { server ->
            server.route("/v1/bookmarks/remove-item") {
                server.response(status = 200, body = """{"status":200}""")
            }

            val result = client(cacheDir, server.url("/v1/"))
                .removeBookmarkItem(itemId = 42, folderId = 7)
            val request = server.takeRequest()

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertEquals("item=42&folder=7", requireNotNull(request.body).utf8())
        }
    }

    private fun client(cacheDir: Path, baseUrl: String): KinoPubApiClient {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } returns true

        val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
        every { preferences.getAccessToken() } returns null
        every { preferences.getRefreshToken() } returns null
        every { preferences.getUsername() } returns null
        every { preferences.getAndroidId() } returns null

        return KinoPubApiClient(
            okHttpClient = OkHttpClient(),
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = preferences,
            sessionEventBus = SessionEventBus(),
            mainApiBaseUrl = baseUrl,
        )
    }
}
