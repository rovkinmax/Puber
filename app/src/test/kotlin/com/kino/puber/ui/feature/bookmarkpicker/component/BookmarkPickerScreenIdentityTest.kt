package com.kino.puber.ui.feature.bookmarkpicker.component

import com.kino.puber.core.ui.navigation.OverlayPuberScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.component.resolveVisibleScreenLayers
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.ScreensImpl
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The picker is rendered over the screen that opened it, and several of them can be opened in one
 * session. That only works while it stays an [OverlayPuberScreen] with a key unique per request:
 * a plain root screen would unmount the screen underneath, and a shared key would collide in
 * Voyager state, TV focus, and the DI scope.
 */
class BookmarkPickerScreenIdentityTest {

    private val screens: Screens = ScreensImpl

    @Test
    fun bookmarkPicker_isAnOverlayScreen() {
        val screen = screens.bookmarkPicker(itemId = 42, resultCode = 1)

        assertTrue(
            screen is OverlayPuberScreen,
            "The picker must render above the screen that opened it, not replace it",
        )
    }

    @Test
    fun bookmarkPicker_keepsTheScreenUnderneathVisible() {
        val background = mockk<PuberScreen>()
        val picker = screens.bookmarkPicker(itemId = 42, resultCode = 1)

        val layers = resolveVisibleScreenLayers(listOf(background, picker))

        assertEquals(listOf(background, picker), layers)
    }

    @Test
    fun bookmarkPicker_usesADistinctKeyPerRequest() {
        val first = screens.bookmarkPicker(itemId = 42, resultCode = 1)
        val secondRequestSameItem =
            screens.bookmarkPicker(itemId = 42, resultCode = 2)
        val otherItem = screens.bookmarkPicker(itemId = 43, resultCode = 1)

        assertNotEquals(first.key, secondRequestSameItem.key)
        assertNotEquals(first.key, otherItem.key)
    }

    @Test
    fun bookmarkPicker_keyIsStableForTheSameRequest() {
        val screen = screens.bookmarkPicker(itemId = 42, resultCode = 1)

        assertEquals(
            screens.bookmarkPicker(itemId = 42, resultCode = 1).key,
            screen.key,
        )
        assertEquals(screen.key, screen.key)
    }
}
