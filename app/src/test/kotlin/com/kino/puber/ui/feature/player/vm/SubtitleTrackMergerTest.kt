package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleTrackMergerTest {

    private val merger = SubtitleTrackMerger(labeler = testLabeler())

    private fun testLabeler() = SubtitleLabeler(
        displayLanguageTag = "ru",
        forcedQualifier = "частичные",
        variantLabel = { label, ordinal -> "$label · вариант $ordinal" },
        unknownLabel = { position -> "Субтитры $position" },
    )

    @Test
    fun merge_usesPlayerTracksAsBackbone_andEnrichesExactIdentity() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(label = "Russian embedded", language = "rus", url = "https://api.test/subtitles/embedded-rus.srt"),
            apiTrack(label = "Russian external", language = "rus", url = "https://api.test/subtitles/external-rus.srt"),
        )
        val playerTracks = listOf(
            playerTrack(label = "external-rus.srt", language = "rus", id = "external-rus.srt", groupIndex = 0),
            playerTrack(label = "Русские полные", language = "ru", id = "hls-russian-full", groupIndex = 1),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(
            listOf("Off", "Русский · вариант 1", "Русский · вариант 2"),
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
            apiTrack("Russian full", "rus", forced = false),
            apiTrack("Russian forced", "rus", forced = true),
        )
        val playerTracks = listOf(
            playerTrack("Russian forced HLS", "ru", "forced", 0, forced = true),
            playerTrack("Russian full HLS", "ru", "full", 1, forced = false),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Русский", "Русский · частичные"), result.map { it.label })
        assertEquals("full", result[1].playerTrackId)
        assertFalse(result[1].isForced!!)
        assertEquals("forced", result[2].playerTrackId)
        assertTrue(result[2].isForced!!)
    }

    @Test
    fun merge_dropsUnmatchedApiTrack_whenPlayerTracksAreAvailable() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(label = "Russian external", language = "rus", url = "https://api.test/subtitles/external.srt"),
        )
        val playerTracks = listOf(
            playerTrack("Russian HLS", "ru", "hls-russian", 0),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Русский"), result.map { it.label })
        assertEquals("", result[1].url)
        assertEquals("hls-russian", result[1].playerTrackId)
    }

    @Test
    fun merge_doesNotTreatDisplayLabelAsTrackIdentity() {
        val apiTrack = apiTrack(label = "Spanish API", language = "spa", url = "https://api.test/subtitles/spanish.srt")
        val playerTrack = playerTrack(label = "spanish.srt", language = "rus", id = "hls-russian", groupIndex = 0)

        val result = merger.merge(listOf(offTrack(), apiTrack), listOf(playerTrack))

        assertEquals("", result[1].url)
        assertEquals("rus", result[1].language)
        assertEquals("hls-russian", result[1].playerTrackId)
    }

    @Test
    fun merge_groupsVariantsByLanguage_keepingManifestOrderWithinEachLanguage() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack("rus #1", "rus"),
            apiTrack("rus #2", "rus"),
            apiTrack("rus #3", "rus"),
            apiTrack("spa", "spa"),
        )
        val playerTracks = listOf(
            playerTrack("Russian 1", "ru", "rus-1", 0),
            playerTrack("Spanish", "es", "spa", 1),
            playerTrack("Russian 2", "ru", "rus-2", 2),
            playerTrack("Russian 3", "ru", "rus-3", 3),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(
            listOf(
                "Off",
                "Русский · вариант 1",
                "Русский · вариант 2",
                "Русский · вариант 3",
                "Испанский",
            ),
            result.map { it.label },
        )
        assertEquals(listOf(null, 0, 2, 3, 1), result.map { it.playerGroupIndex })
        assertEquals(listOf("rus-1", "rus-2", "rus-3", "spa"), result.drop(1).map { it.playerTrackId })
    }

    @Test
    fun merge_usesApiFilePathToMatchHlsRenditionUri_whenLanguageOrderDiffers() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack(label = "rus #1", language = "rus", sourceFile = "/a/71/first.srt"),
            apiTrack(label = "rus #2", language = "rus", sourceFile = "/b/82/second.srt"),
        )
        val playerTracks = listOf(
            playerTrack(
                label = "Russian 2",
                language = "ru",
                id = "subs:Russian #02",
                groupIndex = 0,
                uri = "https://cdn.test/pd/subtitle/token/b/82/second.srt",
            ),
            playerTrack(
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
            label = "Russian external",
            language = "rus",
            url = "https://old-cdn.test/subtitles/russian.vtt?token=expired",
        )
        val playerTrack = playerTrack(
            label = "russian.vtt",
            language = "ru",
            id = "https://new-cdn.test/subtitles/russian.vtt?token=fresh",
            groupIndex = 0,
        )

        val result = merger.merge(listOf(offTrack(), apiTrack), listOf(playerTrack))

        assertEquals(listOf("Off", "Русский"), result.map { it.label })
        assertEquals(playerTrack.playerTrackId, result[1].playerTrackId)
        assertEquals("ru", result[1].language)
        assertEquals(apiTrack.url, result[1].url)
    }

    @Test
    fun merge_exposesOnlyOff_untilPlayerTracksAreDiscovered() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack("Russian full", "rus"),
            apiTrack("Russian forced", "rus"),
        )

        val result = merger.merge(apiTracks, emptyList())

        assertEquals(listOf("Off"), result.map { it.label })
    }

    @Test
    fun merge_marksPartialVariant_andOrdersItLastWithinItsLanguage() {
        val playerTracks = listOf(
            playerTrack("rus", "rus", "full", 0, forced = false, descriptiveLabel = "RUS"),
            playerTrack("rus", "rus", "forced", 1, forced = true, descriptiveLabel = "RUS"),
        )

        val result = merger.merge(listOf(offTrack()), playerTracks)

        assertEquals(listOf("Off", "Русский", "Русский · частичные"), result.map { it.label })
        assertEquals(listOf(null, false, true), result.map { it.isForced })
    }

    /**
     * Real KinoPub data: the API lists two subtitles, the manifest publishes three
     * renditions covering both languages, and Media3 prefixes side-loaded track ids with
     * the merged child index. The renditions win and the side-loaded copies disappear.
     */
    @Test
    fun merge_hidesSideLoadedCopies_whenManifestCoversTheirLanguages() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack("eng", "eng", url = "https://api.test/pd/xyz", sourceFile = "/9/24/2871466.srt"),
            apiTrack("rus", "rus", url = "https://api.test/pd/abc", sourceFile = "/1/92/2871463.srt"),
        )
        val playerTracks = listOf(
            playerTrack("", "ru", "0:", 0, uri = "https://cdn.test/hls/rus01.m3u8", descriptiveLabel = "RUS #01"),
            playerTrack("", "ru", "0:", 1, uri = "https://cdn.test/hls/rus02.m3u8", descriptiveLabel = "RUS #02"),
            playerTrack("", "en", "0:", 2, uri = "https://cdn.test/hls/eng03.m3u8", descriptiveLabel = "ENG #03"),
            playerTrack("", "en", "1:9/24/2871466.srt", 3),
            playerTrack("", "ru", "2:1/92/2871463.srt", 4),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(
            listOf("Off", "Русский · вариант 1", "Русский · вариант 2", "Английский"),
            result.map { it.label },
        )
    }

    /**
     * The complement of the case above: with no renditions in the manifest the side-loaded
     * tracks are the only subtitles there are, so they stay and pick up their API metadata
     * through the merged child index prefix Media3 puts on their ids.
     */
    @Test
    fun merge_carriesSideLoadedTracks_whenTheManifestPublishesNoSubtitles() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack("rus", "rus", url = "https://api.test/pd/a", sourceFile = "/1/11/1.srt"),
            apiTrack("eng", "eng", url = "https://api.test/pd/b", sourceFile = "/2/22/2.srt"),
        )
        val playerTracks = listOf(
            playerTrack("", "ru", "1:1/11/1.srt", 0),
            playerTrack("", "en", "2:2/22/2.srt", 1),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Русский", "Английский"), result.map { it.label })
        assertEquals(
            listOf("https://api.test/pd/a", "https://api.test/pd/b"),
            result.drop(1).map { it.url },
        )
    }

    @Test
    fun merge_hidesSideLoadedTrack_evenWhenItsLanguageIsMissingFromTheManifest() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack("rus", "rus", url = "https://api.test/subtitles/rus.srt"),
            apiTrack("eng", "eng", url = "https://api.test/subtitles/eng.srt"),
        )
        val playerTracks = listOf(
            playerTrack(
                label = "rus",
                language = "rus",
                id = "hls-rus",
                groupIndex = 0,
                uri = "https://cdn.test/subtitles/rus.srt",
            ),
            playerTrack("eng", "eng", "eng.srt", 1),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "Русский"), result.map { it.label })
    }

    @Test
    fun merge_keepsBothManifestRenditions_whenTheyResolveToOneApiEntry() {
        val apiTracks = listOf(
            offTrack(),
            apiTrack("rus", "rus", url = "https://api.test/subtitles/rus.srt"),
        )
        val playerTracks = listOf(
            playerTrack(
                label = "rus",
                language = "rus",
                id = "a",
                groupIndex = 0,
                uri = "https://cdn.test/subtitles/rus.srt",
                descriptiveLabel = "RUS A",
            ),
            playerTrack(
                label = "rus",
                language = "rus",
                id = "b",
                groupIndex = 1,
                uri = "https://cdn.test/subtitles/rus.srt",
                descriptiveLabel = "RUS B",
            ),
        )

        val result = merger.merge(apiTracks, playerTracks)

        assertEquals(listOf("Off", "RUS A", "RUS B"), result.map { it.label })
    }

    private fun offTrack() = SubtitleTrackUIState(
        label = "Off",
        language = "",
        url = "",
    )

    private fun apiTrack(
        label: String,
        language: String,
        url: String = "",
        forced: Boolean? = null,
        sourceFile: String? = null,
    ) = SubtitleTrackUIState(
        label = label,
        language = language,
        url = url,
        isForced = forced,
        sourceFile = sourceFile,
    )

    private fun playerTrack(
        label: String,
        language: String,
        id: String,
        groupIndex: Int,
        forced: Boolean = false,
        uri: String? = null,
        descriptiveLabel: String? = null,
    ) = SubtitleTrackUIState(
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
