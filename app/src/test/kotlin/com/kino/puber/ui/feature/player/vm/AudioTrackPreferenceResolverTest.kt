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

    @Test
    fun findSubtitleTrackIndex_usesLanguageForUrlLessManifestTrack() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "en", url = "", playerTrackId = "hls-english"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "eng",
            preferredUrl = "",
        )

        assertEquals(1, result)
    }

    @Test
    fun findSubtitleTrackIndex_preservesExplicitOffPreference() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "eng", url = "", playerTrackId = "hls-english"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "",
            preferredUrl = "",
        )

        assertEquals(0, result)
    }

    @Test
    fun findSubtitleTrackIndex_prefersCurrentPlayerIdentity_overLanguageFallback() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "", playerTrackId = "full"),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "forced"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = "",
            preferredPlayerTrackId = "forced",
        )

        assertEquals(2, result)
    }

    @Test
    fun findSubtitleTrackIndex_prefersSavedUrl_overCurrentPlayerIdentity() {
        val externalUrl = "https://cdn.test/subtitles/russian.vtt"
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = externalUrl),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "hls-russian"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = externalUrl,
            preferredPlayerTrackId = "hls-russian",
        )

        assertEquals(1, result)
    }

    @Test
    fun findSubtitleTrackIndex_returnsNoMatch_untilPreferredManifestLanguageAppears() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "https://cdn.test/subtitles/russian.vtt"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "ukr",
            preferredUrl = "",
        )

        assertEquals(-1, result)
    }

    private fun subtitleTrack(
        index: Int,
        language: String,
        url: String,
        playerTrackId: String? = null,
    ) = SubtitleTrackUIState(
        index = index,
        label = "Track $index",
        language = language,
        url = url,
        playerTrackId = playerTrackId,
        playerGroupIndex = playerTrackId?.let { index - 1 },
        playerTrackIndex = playerTrackId?.let { 0 },
    )
}
