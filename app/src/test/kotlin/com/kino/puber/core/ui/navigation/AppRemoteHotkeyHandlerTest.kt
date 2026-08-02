package com.kino.puber.core.ui.navigation

import android.view.KeyEvent
import com.kino.puber.ui.feature.auth.component.AuthScreen
import com.kino.puber.ui.feature.root.component.LauncherScreen
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AppRemoteHotkeyHandlerTest {

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var currentScreen: PuberScreen
    private lateinit var searchScreen: PuberScreen
    private lateinit var settingsScreen: PuberScreen

    @BeforeEach
    fun setUp() {
        router = mockk(relaxed = true)
        screens = mockk()
        currentScreen = mockk()
        searchScreen = mockk()
        settingsScreen = mockk()

        every { router.screens } returns screens
        every { currentScreen.key } returns "CurrentScreen"
        every { searchScreen.key } returns "SearchScreen"
        every { settingsScreen.key } returns "SettingsScreen"
        every { screens.search() } returns searchScreen
        every { screens.deviceSettings() } returns settingsScreen
    }

    @Test
    fun searchKeys_navigateToSearchOnFirstDown() {
        listOf(
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST,
        ).forEach { keyCode ->
            assertTrue(handle(keyCode))
        }

        verify(exactly = 3) { router.navigateTo(searchScreen) }
        verify(exactly = 3) { screens.search() }
        verify(exactly = 0) { screens.deviceSettings() }
    }

    @Test
    fun settingsKey_navigatesToDeviceSettingsOnFirstDown() {
        assertTrue(handle(KeyEvent.KEYCODE_SETTINGS))

        verify(exactly = 1) { router.navigateTo(settingsScreen) }
        verify(exactly = 1) { screens.deviceSettings() }
        verify(exactly = 0) { screens.search() }
    }

    @Test
    fun recognizedKey_repeatedDownAndUpAreConsumedWithoutNavigatingAgain() {
        assertTrue(handle(KeyEvent.KEYCODE_SEARCH))
        assertTrue(handle(KeyEvent.KEYCODE_SEARCH, repeatCount = 1))
        assertTrue(handle(KeyEvent.KEYCODE_SEARCH, action = KeyEvent.ACTION_UP))

        verify(exactly = 1) { router.navigateTo(searchScreen) }
        verify(exactly = 1) { screens.search() }
    }

    @Test
    fun sameTarget_isConsumedWithoutPushingDuplicateDestination() {
        every { currentScreen.key } returns searchScreen.key

        assertTrue(handle(KeyEvent.KEYCODE_SEARCH))

        verify(exactly = 1) { screens.search() }
        verify(exactly = 0) { router.navigateTo(any()) }
    }

    @Test
    fun unrelatedKeys_areLeftUnhandled() {
        listOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_TV_CONTENTS_MENU,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_VOLUME_UP,
        ).forEach { keyCode ->
            assertFalse(handle(keyCode))
        }

        verify(exactly = 0) { router.navigateTo(any()) }
        verify(exactly = 0) { screens.search() }
        verify(exactly = 0) { screens.deviceSettings() }
    }

    @Test
    fun nonDownOrUpAction_isLeftUnhandled() {
        assertFalse(handle(KeyEvent.KEYCODE_SEARCH, action = KeyEvent.ACTION_MULTIPLE))

        verify(exactly = 0) { router.navigateTo(any()) }
        verify(exactly = 0) { screens.search() }
    }

    @Test
    fun launcherAndAuthScreens_blockGlobalNavigation() {
        listOf<PuberScreen>(LauncherScreen(), AuthScreen()).forEach { blockedScreen ->
            assertFalse(
                AppRemoteHotkeyHandler.handle(
                    event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH),
                    router = router,
                    currentScreen = blockedScreen,
                )
            )
        }

        verify(exactly = 0) { router.navigateTo(any()) }
        verify(exactly = 0) { screens.search() }
        verify(exactly = 0) { screens.deviceSettings() }
    }

    private fun handle(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
    ): Boolean {
        return AppRemoteHotkeyHandler.handle(
            event = mockk {
                every { this@mockk.keyCode } returns keyCode
                every { this@mockk.action } returns action
                every { this@mockk.repeatCount } returns repeatCount
            },
            router = router,
            currentScreen = currentScreen,
        )
    }
}
