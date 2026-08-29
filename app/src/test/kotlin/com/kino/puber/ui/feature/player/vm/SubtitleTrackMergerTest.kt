package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleTrackMergerTest {

    private val merger = SubtitleTrackMerger(variantLabel = { label, ordinal -> "$label #$ordinal" })

    @Test
    fun subtitleTrackDisplayLabel_hidesManifestNumberingAndUsesLowercaseIso3Language() {
        assertEquals("rus", subtitleTrackDisplayLabel("RU", "RUS #03"))
        assertEquals("spa", subtitleTrackDisplayLabel("es-ES", "SPA #01"))
        assertEquals("Unknown", subtitleTrackDisplayLabel("", "Unknown"))
    }

    @Test
    fun merge_usesPlayerTracksAsBackbone_andEnrichesExactIdentity() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(
                index = 1,
                label = "Russian embedded",
                language = "rus",
                url = "https://api.test/subtitles/embedded-rus.srt",
            ),
            apiTrack(
                index = 2,
                label = "Russian external",
                language = "rus",
                url = "https://api.test/subtitles/external-rus.srt",
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
            listOf("Off", "external-rus.srt", "Русские полные"),
            result.map { it.label },
        )
        assertEquals("external-rus.srt", result[1].playerTrackId)
        assertEquals("https://api.test/subtitles/external-rus.srt", result[1].url)
        assertEquals("hls-russian-full", result[2].playerTrackId)
        assertEquals("ru", result[2].language)
    }

    @Test
    fun merge_doesNotPairSameLanguageVariants_withoutExactIdentity() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(1, "Russian full", "rus", forced = false),
            apiTrack(2, "Russian forced", "rus", forced = true),
        )
        val playerTracks = listOf(
            playerTrack(1, "Russian forced HLS", "ru", "forced", 0, forced = true),
            playerTrack(2, "Russian full HLS", "ru", "full", 1, forced = false),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Russian forced HLS", "Russian full HLS"), result.map { it.label })
        assertEquals("forced", result[1].playerTrackId)
        assertTrue(result[1].isForced!!)
        assertEquals("full", result[2].playerTrackId)
        assertFalse(result[2].isForced!!)
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
    fun merge_dropsUnmatchedApiTrack_whenPlayerTracksAreAvailable() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(
                index = 1,
                label = "Russian external",
                language = "rus",
                url = "https://api.test/subtitles/external.srt",
            ),
        )
        val playerTracks = listOf(
            playerTrack(1, "Russian HLS", "ru", "hls-russian", 0),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Russian HLS"), result.map { it.label })
        assertEquals("", result[1].url)
        assertEquals("hls-russian", result[1].playerTrackId)
    }

    @Test
    fun merge_doesNotTreatDisplayLabelAsTrackIdentity() {
        val apiTrack = apiTrack(
            index = 1,
            label = "Spanish API",
            language = "spa",
            url = "https://api.test/subtitles/spanish.srt",
        )
        val playerTrack = playerTrack(
            index = 1,
            label = "spanish.srt",
            language = "rus",
            id = "hls-russian",
            groupIndex = 0,
        )

        val result = merger.merge(listOf(offTrack(), apiTrack), listOf(playerTrack))

        assertEquals("", result[1].url)
        assertEquals("rus", result[1].language)
        assertEquals("hls-russian", result[1].playerTrackId)
    }

    @Test
    fun merge_keepsManifestOrder_forSameLanguageVariants() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(1, "rus #1", "rus"),
            apiTrack(2, "rus #2", "rus"),
            apiTrack(3, "rus #3", "rus"),
            apiTrack(4, "spa", "spa"),
        )
        val playerTracks = listOf(
            playerTrack(1, "Russian 1", "ru", "rus-1", 0),
            playerTrack(2, "Spanish", "es", "spa", 1),
            playerTrack(3, "Russian 2", "ru", "rus-2", 2),
            playerTrack(4, "Russian 3", "ru", "rus-3", 3),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(
            listOf("Off", "Russian 1", "Spanish", "Russian 2", "Russian 3"),
            result.map { it.label },
        )
        assertEquals(listOf(null, 0, 1, 2, 3), result.map { it.playerGroupIndex })
        assertEquals("rus-3", result[4].playerTrackId)
    }

    @Test
    fun merge_usesApiFilePathToMatchHlsRenditionUri_whenLanguageOrderDiffers() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(
                index = 1,
                label = "rus #1",
                language = "rus",
                sourceFile = "/a/71/first.srt",
            ),
            apiTrack(
                index = 2,
                label = "rus #2",
                language = "rus",
                sourceFile = "/b/82/second.srt",
            ),
        )
        val playerTracks = listOf(
            playerTrack(
                index = 1,
                label = "Russian 2",
                language = "ru",
                id = "subs:Russian #02",
                groupIndex = 0,
                uri = "https://cdn.test/pd/subtitle/token/b/82/second.srt",
            ),
            playerTrack(
                index = 2,
                label = "Russian 1",
                language = "ru",
                id = "subs:Russian #01",
                groupIndex = 1,
                uri = "https://cdn.test/pd/subtitle/token/a/71/first.srt",
            ),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf(0, 1), result.drop(1).map { it.playerGroupIndex })
        assertEquals(
            listOf("subs:Russian #02", "subs:Russian #01"),
            result.drop(1).map { it.playerTrackId },
        )
        assertEquals(
            listOf("/b/82/second.srt", "/a/71/first.srt"),
            result.drop(1).map { it.sourceFile },
        )
    }

    @Test
    fun merge_matchesSideLoadedTrackByStableUrl_whenHostAndTokenChange() {
        val apiTrack = apiTrack(
            index = 1,
            label = "Russian external",
            language = "rus",
            url = "https://old-cdn.test/subtitles/russian.vtt?token=expired",
        )
        val playerTrack = playerTrack(
            index = 1,
            label = "russian.vtt",
            language = "ru",
            id = "https://new-cdn.test/subtitles/russian.vtt?token=fresh",
            groupIndex = 0,
        )

        val result = merger.merge(listOf(offTrack(), apiTrack), listOf(playerTrack))

        assertEquals(listOf("Off", "russian.vtt"), result.map { it.label })
        assertEquals(playerTrack.playerTrackId, result[1].playerTrackId)
        assertEquals("ru", result[1].language)
        assertEquals(apiTrack.url, result[1].url)
    }

    @Test
    fun merge_usesOnlyManifestTracks_whenApiIdentityDoesNotMatch() {
        val embeddedRussian = apiTrack(
            index = 1,
            label = "Russian embedded",
            language = "rus",
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

        assertEquals(listOf("Off", "English HLS"), result.map { it.label })
        assertEquals(listOf("", "en"), result.map { it.language })
        assertEquals("hls-english", result[1].playerTrackId)
    }

    @Test
    fun merge_exposesOnlyOff_untilPlayerTracksAreDiscovered() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(1, "Russian full", "rus"),
            apiTrack(2, "Russian forced", "rus"),
        )

        val result = merger.merge(apiTracks, emptyList())

        assertEquals(listOf("Off"), result.map { it.label })
        assertEquals(listOf(0), result.map { it.index })
    }

    @Test
    fun merge_usesDescriptiveLabels_forSameLanguageVariantsThatWouldCollide() {
        val playerTracks = listOf(
            playerTrack(1, "rus", "rus", "full", 0, descriptiveLabel = "Русские полные"),
            playerTrack(2, "rus", "rus", "sdh", 1, descriptiveLabel = "Русские SDH"),
        )

        val result = merger.merge(listOf(offTrack()), playerTracks)

        assertEquals(listOf("Off", "Русские полные", "Русские SDH"), result.map { it.label })
    }

    @Test
    fun merge_numbersSameLanguageVariants_whenDescriptiveLabelsCannotSeparateThem() {
        val playerTracks = listOf(
            playerTrack(1, "rus", "rus", "a", 0, descriptiveLabel = "RUS"),
            playerTrack(2, "rus", "rus", "b", 1, descriptiveLabel = "RUS"),
            playerTrack(3, "rus", "rus", "c", 2, descriptiveLabel = null),
        )

        val result = merger.merge(listOf(offTrack()), playerTracks)

        assertEquals(listOf("Off", "rus #1", "rus #2", "rus #3"), result.map { it.label })
    }

    @Test
    fun merge_keepsForcedVariantUntouched_becauseThePickerAlreadyMarksIt() {
        val playerTracks = listOf(
            playerTrack(1, "rus", "rus", "full", 0, forced = false, descriptiveLabel = "RUS"),
            playerTrack(2, "rus", "rus", "forced", 1, forced = true, descriptiveLabel = "RUS"),
        )

        val result = merger.merge(listOf(offTrack()), playerTracks)

        assertEquals(listOf("Off", "rus", "rus"), result.map { it.label })
        assertEquals(listOf(null, false, true), result.map { it.isForced })
    }

    @Test
    fun merge_leavesDistinctLabelsAlone() {
        val playerTracks = listOf(
            playerTrack(1, "rus", "rus", "a", 0, descriptiveLabel = "RUS"),
            playerTrack(2, "eng", "eng", "b", 1, descriptiveLabel = "ENG"),
        )

        val result = merger.merge(listOf(offTrack()), playerTracks)

        assertEquals(listOf("Off", "rus", "eng"), result.map { it.label })
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
        forced: Boolean? = null,
        sourceFile: String? = null,
    ) = SubtitleTrackUIState(
        index = index,
        label = label,
        language = language,
        url = url,
        isForced = forced,
        sourceFile = sourceFile,
    )

    private fun playerTrack(
        index: Int,
        label: String,
        language: String,
        id: String,
        groupIndex: Int,
        forced: Boolean = false,
        uri: String? = null,
        descriptiveLabel: String? = null,
    ) = SubtitleTrackUIState(
        index = index,
        label = label,
        language = language,
        url = "",
        isForced = forced,
        descriptiveLabel = descriptiveLabel,
        playerTrackId = id,
        playerTrackUri = uri,
        playerGroupIndex = groupIndex,
        playerTrackIndex = 0,
    )
}
