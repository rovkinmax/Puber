package com.kino.puber.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.auth.component.AuthScreen
import com.kino.puber.ui.feature.details.component.DetailsScreen
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.device.settings.flow.DeviceSettingsFlowScreen
import com.kino.puber.ui.feature.favorites.content.FavoritesScreen
import com.kino.puber.ui.feature.main.component.MainScreen
import kotlinx.parcelize.Parcelize

internal object ScreensImpl : Screens {
    override fun auth(): PuberScreen {
        return AuthScreen()
    }

    override fun main(): PuberScreen {
        return MainScreen()
    }

    override fun favorites(): PuberScreen {
        return FavoritesScreen()
    }

    override fun deviceSettings(): PuberScreen {
        return DeviceSettingsFlowScreen()
    }

    override fun underDevelopment(): PuberScreen {
        return UnderDevelopment()
    }

    override fun details(itemId: Int): PuberScreen {
        return DetailsScreen(DetailsScreenParams(itemId))
    }


    @Parcelize
    private class UnderDevelopment : PuberScreen {
        @Composable
        override fun Content() {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Under development")
            }

        }
    }
}