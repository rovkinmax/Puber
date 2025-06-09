package com.kino.puber.ui

import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.auth.component.AuthScreen
import com.kino.puber.ui.feature.main.component.MainScreen

internal object ScreensImpl : Screens {
    override fun auth(): PuberScreen {
        return AuthScreen()
    }

    override fun main(): PuberScreen {
        return MainScreen()
    }

}