package com.kino.puber.core.ui.navigation

import android.os.Parcelable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey

interface PuberScreen : Screen, Parcelable {
    override val key: ScreenKey
        get() = javaClass.simpleName
}