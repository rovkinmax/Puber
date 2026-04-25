package com.kino.puber.baselineprofile

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
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun browseAndDetailsProfile() = baselineProfileRule.collect(
        packageName = packageName,
    ) {
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
