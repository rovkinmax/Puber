package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AudioTrackPreferenceResolverTest {

    private val resolver = AudioTrackPreferenceResolver()

    @Test
    fun findAudioTrackIndex_prefersExactThenNormalizedLabel() {
        val tracks = listOf(
            AudioTrackUIState(0, "01. Original (ENG)", "eng"),
            AudioTrackUIState(1, "02. Original (ENG)", "eng"),
            AudioTrackUIState(2, "03. Многоголосый. Red Head Sound (RUS)", "rus"),
        )

        assertEquals(1, resolver.findAudioTrackIndex(tracks, "02. Original (ENG)", "eng"))
        assertEquals(0, resolver.findAudioTrackIndex(tracks, "1. Original (ENG)", "eng"))
        assertEquals(2, resolver.findAudioTrackIndex(tracks, "99. Многоголосый. Other (RUS)", "rus"))
    }

    @Test
    fun findAudioTrackIndex_fallsBackToLanguageAndReturnsNoMatch() {
        val tracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Русский", "rus"),
        )

        assertEquals(1, resolver.findAudioTrackIndex(tracks, null, "rus"))
        assertEquals(-1, resolver.findAudioTrackIndex(tracks, null, "deu"))
    }

    @Test
    fun findSubtitleTrackIndex_matchesSignedUrlByStablePathBeforeLanguage() {
        val tracks = listOf(
            SubtitleTrackUIState(0, "Off", "", ""),
            SubtitleTrackUIState(1, "Russian", "rus", "https://cdn/subtitles/rus.vtt?token=new"),
            SubtitleTrackUIState(2, "Russian SDH", "rus", "https://cdn/subtitles/rus-sdh.vtt?token=new"),
        )

        assertEquals(
            1,
            resolver.findSubtitleTrackIndex(
                tracks = tracks,
                preferredLang = "rus",
                preferredUrl = "https://cdn/subtitles/rus.vtt?token=old#signed",
            ),
        )
    }

    @Test
    fun findSubtitleTrackIndex_rejectsAmbiguousLanguageWithoutUrl() {
        val tracks = listOf(
            SubtitleTrackUIState(0, "Off", "", ""),
            SubtitleTrackUIState(1, "Russian", "rus", "https://cdn/subtitles/rus.vtt"),
            SubtitleTrackUIState(2, "Russian SDH", "rus", "https://cdn/subtitles/rus-sdh.vtt"),
        )

        assertEquals(-1, resolver.findSubtitleTrackIndex(tracks, "rus", null))
    }
}
