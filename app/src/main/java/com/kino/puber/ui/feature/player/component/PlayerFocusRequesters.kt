package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

internal class PlayerFocusRequesters {
    val player = FocusRequester()
    val firstButton = FocusRequester()
    val episodesButton = FocusRequester()
    val audioSubtitlesButton = FocusRequester()
    val videoSettingsButton = FocusRequester()
    val seekBar = FocusRequester()
}

@Composable
internal fun rememberPlayerFocusRequesters(): PlayerFocusRequesters {
    return remember { PlayerFocusRequesters() }
}
