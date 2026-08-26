package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleTrackMergerTest {

    private val merger = SubtitleTrackMerger()

    @Test
    fun merge_matchesExactExternalIdentity_beforeEmbeddedLanguage() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(
                index = 1,
                label = "Russian embedded",
                language = "rus",
                url = "https://api.test/subtitles/embedded-rus.srt",
                embedded = true,
            ),
            apiTrack(
                index = 2,
                label = "Russian external",
                language = "rus",
                url = "https://api.test/subtitles/external-rus.srt",
                embedded = false,
            ),
        )
        val playerTracks = listOf(
            playerTrack(
                index = 1,
                label = "external-rus.srt",
                language = "rus",
                id = "external-rus.srt",
                groupIndex = 0,
            ),
            playerTrack(
                index = 2,
                label = "Русские полные",
                language = "ru",
                id = "hls-russian-full",
                groupIndex = 1,
            ),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(
            listOf("Off", "Russian embedded", "Russian external"),
            result.map { it.label },
        )
        assertEquals("hls-russian-full", result[1].playerTrackId)
        assertEquals("rus", result[1].language)
        assertEquals("external-rus.srt", result[2].playerTrackId)
    }

    @Test
    fun merge_matchesEmbeddedVariants_byForcedMetadata() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(1, "Russian full", "rus", embedded = true, forced = false),
            apiTrack(2, "Russian forced", "rus", embedded = true, forced = true),
        )
        val playerTracks = listOf(
            playerTrack(1, "Russian forced HLS", "ru", "forced", 0, forced = true),
            playerTrack(2, "Russian full HLS", "ru", "full", 1, forced = false),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals("full", result[1].playerTrackId)
        assertFalse(result[1].isForced!!)
        assertEquals("forced", result[2].playerTrackId)
        assertTrue(result[2].isForced!!)
    }

    @Test
    fun merge_appendsManifestOnlyTracks_withoutChangingTheirLanguage() {
        val playerTrack = playerTrack(
            index = 7,
            label = "Українські",
            language = "uk",
            id = "hls-ukrainian",
            groupIndex = 2,
        )

        val result = merger.merge(listOf(offTrack()), listOf(playerTrack))

        assertEquals(listOf("", "uk"), result.map { it.language })
        assertEquals(listOf(0, 1), result.map { it.index })
        assertEquals("hls-ukrainian", result[1].playerTrackId)
        assertEquals(2, result[1].playerGroupIndex)
    }

    @Test
    fun merge_doesNotCollapseExternalAndManifestTracks_byLanguageAlone() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(
                index = 1,
                label = "Russian external",
                language = "rus",
                url = "https://api.test/subtitles/external.srt",
                embedded = false,
            ),
        )
        val playerTracks = listOf(
            playerTrack(1, "Russian HLS", "ru", "hls-russian", 0),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Russian external", "Russian HLS"), result.map { it.label })
        assertEquals("https://api.test/subtitles/external.srt", result[1].url)
        assertEquals("hls-russian", result[2].playerTrackId)
    }

    @Test
    fun merge_matchesSideLoadedTrackByStableUrl_whenHostAndTokenChange() {
        val apiTrack = apiTrack(
            index = 1,
            label = "Russian external",
            language = "rus",
            url = "https://old-cdn.test/subtitles/russian.vtt?token=expired",
            embedded = false,
        )
        val playerTrack = playerTrack(
            index = 1,
            label = "russian.vtt",
            language = "ru",
            id = "https://new-cdn.test/subtitles/russian.vtt?token=fresh",
            groupIndex = 0,
        )

        val result = merger.merge(listOf(offTrack(), apiTrack), listOf(playerTrack))

        assertEquals(listOf("Off", "Russian external"), result.map { it.label })
        assertEquals(playerTrack.playerTrackId, result[1].playerTrackId)
        assertEquals("rus", result[1].language)
    }

    @Test
    fun merge_keepsUnmatchedEmbeddedAndManifestTracks_withoutFalseLanguageMatch() {
        val embeddedRussian = apiTrack(
            index = 1,
            label = "Russian embedded",
            language = "rus",
            embedded = true,
        )
        val manifestEnglish = playerTrack(
            index = 1,
            label = "English HLS",
            language = "en",
            id = "hls-english",
            groupIndex = 0,
        )

        val result = merger.merge(
            listOf(offTrack(), embeddedRussian),
            listOf(manifestEnglish),
        )

        assertEquals(listOf("Off", "Russian embedded", "English HLS"), result.map { it.label })
        assertEquals(listOf("", "rus", "en"), result.map { it.language })
        assertEquals(null, result[1].playerTrackId)
        assertEquals("hls-english", result[2].playerTrackId)
    }

    private fun offTrack() = SubtitleTrackUIState(
        index = 0,
        label = "Off",
        language = "",
        url = "",
    )

    private fun apiTrack(
        index: Int,
        label: String,
        language: String,
        url: String = "",
        embedded: Boolean,
        forced: Boolean? = null,
    ) = SubtitleTrackUIState(
        index = index,
        label = label,
        language = language,
        url = url,
        isEmbedded = embedded,
        isForced = forced,
    )

    private fun playerTrack(
        index: Int,
        label: String,
        language: String,
        id: String,
        groupIndex: Int,
        forced: Boolean = false,
    ) = SubtitleTrackUIState(
        index = index,
        label = label,
        language = language,
        url = "",
        isEmbedded = true,
        isForced = forced,
        playerTrackId = id,
        playerGroupIndex = groupIndex,
        playerTrackIndex = 0,
    )
}
