package com.kino.puber.core.ui.navigation

import android.view.KeyEvent

internal interface GlobalRemoteHotkeyBlockedScreen

internal enum class AppRemoteHotkeyAction {
    Search,
    Settings,
}

internal object AppRemoteHotkeyHandler {

    fun handle(
        event: KeyEvent,
        router: AppRouter,
        currentScreen: PuberScreen,
    ): Boolean {
        if (currentScreen is GlobalRemoteHotkeyBlockedScreen) {
            return false
        }

        val action = event.keyCode.toRemoteHotkeyAction() ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val target = when (action) {
                AppRemoteHotkeyAction.Search -> router.screens.search()
                AppRemoteHotkeyAction.Settings -> router.screens.deviceSettings()
            }
            if (target.key != currentScreen.key) {
                router.navigateTo(target)
            }
        }

        return event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
    }

    private fun Int.toRemoteHotkeyAction(): AppRemoteHotkeyAction? = when (this) {
        KeyEvent.KEYCODE_SEARCH,
        KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_VOICE_ASSIST,
        -> AppRemoteHotkeyAction.Search

        KeyEvent.KEYCODE_SETTINGS -> AppRemoteHotkeyAction.Settings
        else -> null
    }
}
