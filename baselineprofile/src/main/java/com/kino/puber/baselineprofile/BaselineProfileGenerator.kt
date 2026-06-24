package com.kino.puber.baselineprofile

import android.os.Bundle
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val packageName: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not found in instrumentation args")

    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = packageName,
        includeInStartupProfile = true,
    ) {
        pressHome()
        seedAuthState()
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun browseAndDetailsProfile() = baselineProfileRule.collect(
        packageName = packageName,
    ) {
        seedAuthState()
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2000)

        // TopTabs: focus starts on TabRow (Home tab)
        // DOWN → enter content area (HeroCarousel / first LazyRow)
        device.pressDPadDown()
        device.waitForIdle()

        // Scroll through vertical sections
        repeat(8) {
            device.pressDPadDown()
            device.waitForIdle()
        }

        // Scroll RIGHT through a horizontal LazyRow
        repeat(5) {
            device.pressDPadRight()
            device.waitForIdle()
        }

        // Scroll back UP towards first card
        repeat(8) {
            device.pressDPadUp()
            device.waitForIdle()
        }

        // SELECT → DetailsScreen
        device.pressDPadCenter()
        device.waitForIdle()
        Thread.sleep(2000)

        // BACK → main screen
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun tabNavigationProfile() = baselineProfileRule.collect(
        packageName = packageName,
    ) {
        seedAuthState()
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2000)

        // TopTabs: focus starts on TabRow (Home tab)
        // RIGHT through tabs: Home → Movies → Series → Collections
        repeat(3) {
            device.pressDPadRight()
            device.waitForIdle()
            Thread.sleep(1000)
        }

        // DOWN → enter content of Collections tab
        device.pressDPadDown()
        device.waitForIdle()

        repeat(3) {
            device.pressDPadDown()
            device.waitForIdle()
        }

        // BACK → returns to TabRow (BackHandler: content → tabRow)
        device.pressBack()
        device.waitForIdle()

        // LEFT back: Collections → Series → Movies → Home
        repeat(3) {
            device.pressDPadLeft()
            device.waitForIdle()
            Thread.sleep(1000)
        }

        // DOWN into Home content + scroll
        device.pressDPadDown()
        device.waitForIdle()

        repeat(5) {
            device.pressDPadDown()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.seedAuthState() {
        val authArgs = BaselineProfileAuthArgs.from(InstrumentationRegistry.getArguments())

        // Macrobenchmark may reinstall or clear target app data between test methods on some devices.
        // Re-seeding tokens in every scenario keeps generated profiles focused on authenticated CUJs.
        val output = device.executeShellCommand(
            buildString {
                append("am broadcast --receiver-foreground ")
                append("-n ${packageName}/$AUTH_RECEIVER_CLASS ")
                append("-a $ACTION_SEED_AUTH ")
                append("--es $EXTRA_ACCESS_TOKEN ${authArgs.accessToken.shellQuote()} ")
                append("--es $EXTRA_REFRESH_TOKEN ${authArgs.refreshToken.shellQuote()} ")
                authArgs.username?.let { append("--es $EXTRA_USERNAME ${it.shellQuote()} ") }
                authArgs.apiDomain?.let { append("--es $EXTRA_API_DOMAIN ${it.shellQuote()} ") }
            }
        )

        check(!output.contains("result=2") && !output.contains("result=3") && !output.contains("Error", true)) {
            "Failed to seed baseline profile auth state. Broadcast output: $output"
        }
    }

    private data class BaselineProfileAuthArgs(
        val accessToken: String,
        val refreshToken: String,
        val username: String?,
        val apiDomain: String?,
    ) {
        companion object {
            fun from(arguments: Bundle): BaselineProfileAuthArgs {
                val accessToken = arguments.getString(ARG_ACCESS_TOKEN)
                val refreshToken = arguments.getString(ARG_REFRESH_TOKEN)
                require(!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                    """
                    Baseline profile generation requires authenticated KinoPub tokens.
                    Run ./tools/generate-baseline-profile.sh and complete the device-code login once,
                    or pass -Pandroid.testInstrumentationRunnerArguments.$ARG_ACCESS_TOKEN and
                    -Pandroid.testInstrumentationRunnerArguments.$ARG_REFRESH_TOKEN manually.
                    """.trimIndent()
                }

                return BaselineProfileAuthArgs(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    username = arguments.getString(ARG_USERNAME)?.takeIf(String::isNotBlank),
                    apiDomain = arguments.getString(ARG_API_DOMAIN)?.takeIf(String::isNotBlank),
                )
            }
        }
    }

    private fun String.shellQuote(): String = buildString {
        append('\'')
        this@shellQuote.forEach { char ->
            if (char == '\'') {
                append("'\\''")
            } else {
                append(char)
            }
        }
        append('\'')
    }

    private companion object {
        private const val AUTH_RECEIVER_CLASS = "com.kino.puber.profile.BaselineProfileAuthReceiver"
        private const val ACTION_SEED_AUTH = "com.kino.puber.profile.SEED_AUTH"

        private const val EXTRA_ACCESS_TOKEN = "access_token"
        private const val EXTRA_REFRESH_TOKEN = "refresh_token"
        private const val EXTRA_USERNAME = "username"
        private const val EXTRA_API_DOMAIN = "api_domain"

        private const val ARG_ACCESS_TOKEN = "puber.baselineProfile.accessToken"
        private const val ARG_REFRESH_TOKEN = "puber.baselineProfile.refreshToken"
        private const val ARG_USERNAME = "puber.baselineProfile.username"
        private const val ARG_API_DOMAIN = "puber.baselineProfile.apiDomain"
    }
}
