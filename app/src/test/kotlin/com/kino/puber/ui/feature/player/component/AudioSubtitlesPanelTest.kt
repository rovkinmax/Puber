package com.kino.puber.ui.feature.player.component

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AudioSubtitlesPanelTest {

    @Test
    fun subtitlePickerLabel_marksForcedTrack_withoutManifestNumbering() {
        val track = subtitleTrack(label = "rus", isForced = true)

        assertEquals("rus · форсированные", track.subtitlePickerLabel("форсированные"))
    }

    @Test
    fun subtitlePickerLabel_keepsRegularTrackLanguageOnly() {
        val track = subtitleTrack(label = "rus", isForced = false)

        assertEquals("rus", track.subtitlePickerLabel("форсированные"))
    }

    private fun subtitleTrack(label: String, isForced: Boolean) = SubtitleTrackUIState(
        index = 1,
        label = label,
        language = "rus",
        url = "",
        isForced = isForced,
    )
}
