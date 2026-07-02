package com.kino.puber.core.ui.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.kino.puber.core.ui.navigation.component.LocalTabAppRouterHolder
import com.kino.puber.core.ui.navigation.component.TabFlowComponent
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Stable
@Parcelize
data class PuberTab(
    private val screen: PuberScreen,
    val tag: Parcelable,
    private val instanceKey: String = "",
) : PuberScreen, Tab {

    @IgnoredOnParcel
    override val key: ScreenKey = buildString {
        append("Tab:")
        append(screen.key)
        if (instanceKey.isNotBlank()) {
            append(":")
            append(instanceKey)
        }
    }

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(index = 0U, title = key, icon = null)
        }

    @Composable
    override fun Content() {
        val holder = LocalTabAppRouterHolder.current
        if (holder == null) {
            Navigator(screen)
            return
        }
        val tabRouter = remember(key) { holder.getOrCreate(key) }
        TabFlowComponent(
            scopeName = key,
            screen = screen,
            tabRouter = tabRouter,
        )
    }
}
