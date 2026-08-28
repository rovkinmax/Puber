package com.kino.puber.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
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

    private lateinit var backend: BaselineMockBackend

    @Before
    fun setUp() {
        backend = BaselineProfileTestControl.createBackend(
            InstrumentationRegistry.getArguments(),
        )
        backend.start()
        backend.awaitReady()
    }

    @After
    fun tearDown() {
        backend.close()
    }

    @Test
    fun startupProfile() {
        collectScenario(
            scenario = BaselineScenario.Startup,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            BaselineProfileTestControl.awaitStartupReady(backend)
            device.waitForIdle()
        }
    }

    @Test
    fun browseAndDetailsProfile() {
        collectScenario(scenario = BaselineScenario.BrowseAndDetails) {
            startActivityAndWait()
            device.waitForIdle()
            Thread.sleep(2000)

            // TopTabs: focus starts on TabRow (Home tab)
            // DOWN → enter content area (HeroCarousel / first LazyRow)
            device.pressDPadDown()
            device.waitForIdle()

            // Open and close the first deterministic Home card before the
            // longer traversal so the details CUJ is always collected.
            device.pressDPadCenter()
            device.waitForIdle()
            Thread.sleep(1000)
            device.pressBack()
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
    }

    @Test
    fun tabNavigationProfile() {
        collectScenario(scenario = BaselineScenario.TabNavigation) {
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
    }

    private fun collectScenario(
        scenario: BaselineScenario,
        includeInStartupProfile: Boolean = false,
        profileBlock: MacrobenchmarkScope.() -> Unit,
    ) {
        BaselineProfileTestControl.prepare(
            targetPackageName = packageName,
            backend = backend,
            scenario = scenario,
        )

        var collectionFailure: Throwable? = null
        try {
            baselineProfileRule.collect(
                packageName = packageName,
                includeInStartupProfile = includeInStartupProfile,
                filterPredicate = BaselineProfileRuleFilter::include,
                profileBlock = profileBlock,
            )
        } catch (failure: Throwable) {
            collectionFailure = failure
            throw failure
        } finally {
            try {
                BaselineProfileTestControl.verify(
                    targetPackageName = packageName,
                    backend = backend,
                )
            } catch (verificationFailure: Throwable) {
                if (collectionFailure == null) {
                    throw verificationFailure
                }
                collectionFailure.addSuppressed(verificationFailure)
            } finally {
                backend.close()
            }
        }
    }
}
