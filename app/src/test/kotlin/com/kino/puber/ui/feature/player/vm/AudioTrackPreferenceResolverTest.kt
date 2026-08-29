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
    fun findSubtitleTrackIndex_usesPlayerCoordinates_whenSelectedFormatLosesItsId() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "eng", url = "", groupIndex = 5),
            subtitleTrack(index = 2, language = "eng", url = "", groupIndex = 6),
            subtitleTrack(index = 3, language = "eng", url = "", groupIndex = 7),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "eng",
            preferredUrl = "",
            preferredPlayerTrackId = "manifest-id-that-disappeared",
            preferredPlayerGroupIndex = 6,
            preferredPlayerTrackIndex = 0,
        )

        assertEquals(2, result)
    }

    @Test
    fun findSubtitleTrackIndex_matchesSavedManifestUri_whenSignedTokenChanges() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(
                index = 1,
                language = "rus",
                url = "",
                playerTrackUri = "https://new.test/pd/subtitle/a/71/russian.srt?token=fresh",
            ),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = "https://old.test/pd/subtitle/a/71/russian.srt?token=expired",
        )

        assertEquals(1, result)
    }

    @Test
    fun findSubtitleTrackIndex_doesNotGuessWhenStableIdentityMatchesMultipleTracks() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(
                index = 1,
                language = "rus",
                url = "",
                playerTrackUri = "https://cdn.test/a/russian.srt",
            ),
            subtitleTrack(
                index = 2,
                language = "rus",
                url = "",
                playerTrackUri = "https://cdn.test/b/russian.srt",
            ),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = "russian.srt",
        )

        assertEquals(-1, result)
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
    fun findSubtitleTrackIndex_usesSavedManifestIdentity_forSameLanguageVariants() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "", playerTrackId = "rus-full"),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "rus-forced"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = "rus-forced",
        )

        assertEquals(2, result)
    }

    @Test
    fun findSubtitleTrackIndex_keepsHashSuffixInOpaqueManifestIdentity() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "", playerTrackId = "subs:Russian #02"),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "subs:Russian #03"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = "subs:Russian #03",
        )

        assertEquals(2, result)
    }

    @Test
    fun findSubtitleTrackIndex_doesNotGuessBetweenSameLanguageVariants() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "", playerTrackId = "rus-full"),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "rus-forced"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = null,
        )

        assertEquals(-1, result)
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
        playerTrackUri: String? = null,
        groupIndex: Int? = playerTrackId?.let { index - 1 },
        trackIndex: Int? = groupIndex?.let { 0 },
    ) = SubtitleTrackUIState(
        label = "Track $index",
        language = language,
        url = url,
        playerTrackId = playerTrackId,
        playerTrackUri = playerTrackUri,
        playerGroupIndex = groupIndex,
        playerTrackIndex = trackIndex,
    )
}
