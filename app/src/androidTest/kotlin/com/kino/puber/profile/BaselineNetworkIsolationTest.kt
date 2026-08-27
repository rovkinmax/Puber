package com.kino.puber.profile

import android.app.Activity
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.data.api.config.ApiEndpointMode
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import java.net.HttpURLConnection
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.GlobalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineNetworkIsolationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loopbackIsAllowedAndExternalOriginsAreRejectedAndJournaled() {
        BaselineInstrumentationEnvironment.configure(context)
        assertEquals(
            Activity.RESULT_OK,
            callControlReceiver(BaselineProfileControlReceiver.ACTION_CLEAR).code,
        )

        MockWebServer().use { server ->
            server.start(com.kino.puber.BuildConfig.BASELINE_MOCK_PORT)
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("ready")
                    .build(),
            )

            val request = Request.Builder()
                .url("${BaselineInstrumentationEnvironment.mockOrigin()}/ready")
                .build()
            OkHttpClient().newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("ready", response.body.string())
            }
        }

        assertFailsWithoutEgress {
            OkHttpClient().newCall(
                Request.Builder()
                    .url("https://example.com/private?token=must-not-be-recorded")
                    .build(),
            ).execute().use { }
        }
        // OkHttp supplies an origin-only route URI to ProxySelector. Exercise
        // the selector's richer URI contract directly, then prove the client
        // request is still rejected by that same selector.
        observeProxyUri(
            "https://journal-user:journal-password@example.com/private" +
                "?token=must-not-be-recorded",
        )

        val verification = callControlReceiver(BaselineProfileControlReceiver.ACTION_VERIFY)
        assertEquals(BaselineProfileControlReceiver.RESULT_VIOLATIONS, verification.code)
        assertTrue(verification.data.contains("https://example.com:443/private"))
        assertFalse(
            verification.data.contains("journal-user") ||
                verification.data.contains("journal-password") ||
                verification.data.contains("token") ||
                verification.data.contains("?"),
        )

        assertEquals(
            Activity.RESULT_OK,
            callControlReceiver(BaselineProfileControlReceiver.ACTION_CLEAR).code,
        )
        val clearedVerification = callControlReceiver(BaselineProfileControlReceiver.ACTION_VERIFY)
        assertEquals(Activity.RESULT_OK, clearedVerification.code)
        assertTrue(clearedVerification.data.isEmpty())
    }

    @Test
    fun ktorAndHttpUrlConnectionAreRejectedByTheSameProcessBlocker() {
        BaselineInstrumentationEnvironment.configure(context)
        assertEquals(
            Activity.RESULT_OK,
            callControlReceiver(BaselineProfileControlReceiver.ACTION_CLEAR).code,
        )

        assertFailsWithoutEgress {
            runBlocking {
                HttpClient(OkHttp).use { client ->
                    client.get("https://example.org/ktor?secret=hidden")
                }
            }
        }

        assertFailsWithoutEgress {
            val connection = URL("http://example.net/legacy?secret=hidden")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = CONNECT_TIMEOUT_MS
            connection.connect()
        }
        observeProxyUri("https://example.org/ktor?secret=hidden")
        observeProxyUri("http://example.net/legacy?secret=hidden")

        val verification = callControlReceiver(BaselineProfileControlReceiver.ACTION_VERIFY)
        assertEquals(BaselineProfileControlReceiver.RESULT_VIOLATIONS, verification.code)
        assertTrue(verification.data.contains("https://example.org:443/ktor"))
        assertTrue(verification.data.contains("http://example.net:80/legacy"))
        assertFalse(verification.data.contains("secret") || verification.data.contains("?"))
    }

    @Test
    fun instrumentationCompositionUsesSyntheticAuthAndDisablesAutomaticUpdates() = runBlocking {
        BaselineInstrumentationEnvironment.configure(context)
        assertEquals(
            Activity.RESULT_OK,
            callControlReceiver(BaselineProfileControlReceiver.ACTION_CLEAR).code,
        )

        val koin = GlobalContext.get()
        val auth = koin.get<ICryptoPreferenceRepository>()
        val updates = koin.get<IAppUpdateInteractor>()

        assertEquals(ApiEndpointMode.PINNED, KinoPubConfig.CURRENT_ENDPOINT_MODE)
        assertEquals(
            BaselineInstrumentationEnvironment.mockOrigin(),
            KinoPubConfig.MAIN_API_BASE_URL.removeSuffix("/v1/"),
        )
        assertEquals("baseline-access-token", auth.getAccessToken())
        assertEquals("baseline-refresh-token", auth.getRefreshToken())
        assertFalse(updates.isAutoCheckEnabled())
        assertEquals(null, updates.checkForUpdate("1.0.0").getOrThrow())
        assertEquals(
            Activity.RESULT_OK,
            callControlReceiver(BaselineProfileControlReceiver.ACTION_VERIFY).code,
        )
    }

    private fun callControlReceiver(action: String): ReceiverResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "${context.packageName}/${BaselineProfileControlReceiver::class.java.name}"
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "am broadcast --receiver-foreground -n $component -a $action",
            ),
        ).bufferedReader().use { it.readText() }
        val result = BROADCAST_RESULT.find(output)
            ?: throw AssertionError("Missing broadcast result: $output")
        return ReceiverResult(
            code = result.groupValues[1].toInt(),
            data = result.groupValues[2],
        )
    }

    private fun assertFailsWithoutEgress(block: () -> Unit) {
        try {
            block()
            throw AssertionError("External request unexpectedly succeeded")
        } catch (error: java.io.IOException) {
            // The deny proxy is a local sink; no external socket is allowed.
        } catch (error: AssertionError) {
            throw error
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }

    private fun observeProxyUri(rawUri: String) {
        val proxies = ProxySelector.getDefault().select(URI(rawUri))
        assertTrue(proxies.none { it == java.net.Proxy.NO_PROXY })
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 1_000
        private val BROADCAST_RESULT = Regex(
            """Broadcast completed: result=(-?\d+)(?:, data="([^"]*)")?""",
        )
    }

    private data class ReceiverResult(
        val code: Int,
        val data: String,
    )
}
