package com.kino.puber.core.ui.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Stable
@Parcelize
data class PuberTab(
    private val screen: PuberScreen,
    val tag: Parcelable,
) : PuberScreen, Tab {

    @IgnoredOnParcel
    override val key: ScreenKey = "Tab:${screen.key}"

    override val options: TabOptions
        @Composable
        get() {
            throw NotImplementedError()
        }

    override fun getBackDispatcher(): BackButtonDispatcher = object : BackButtonDispatcher {}

    @Composable
    override fun Content() {
        Navigator(screen)
    }
}