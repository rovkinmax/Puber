package com.kino.puber.core.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.parcelize.IgnoredOnParcel

abstract class PuberScreenActivity(screenKey: String? = null) : PuberScreen {
    @IgnoredOnParcel
    override val key: ScreenKey by lazy { screenKey ?: this::class.java.name }

    open fun getActivityIntent(context: Context): Intent? {
        return null
    }

    @Composable
    override fun Content() {
        throw UnsupportedOperationException("Check that you are correctly using the PuberScreenActivity ")
    }
}