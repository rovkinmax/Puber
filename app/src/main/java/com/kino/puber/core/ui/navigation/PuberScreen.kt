package com.kino.puber.core.ui.navigation

import android.os.Parcelable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey

interface PuberScreen : Screen, Parcelable {
    override val key: ScreenKey
        get() = javaClass.simpleName
}

interface RootPuberScreen : PuberScreen

/** A root screen rendered over the previous root screen instead of replacing it visually. */
interface OverlayPuberScreen : RootPuberScreen

interface FullscreenPuberScreen : RootPuberScreen
