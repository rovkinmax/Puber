package com.kino.puber.core.ui.root.component

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.ui.navigation.PuberScreen
import kotlinx.parcelize.Parcelize

@Parcelize
internal class RootScreen : PuberScreen {
    override val key: ScreenKey
        get() = Key

    @Composable
    override fun Content() {

    }

    companion object {
        const val Key = "RootScreen"
    }
}