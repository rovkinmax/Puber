package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SubtitleTrackSelectorTest {

    private val selector = SubtitleTrackSelector()

    /**
     * The regression that made merged HLS playback unusable before: the picker row is
     * backed by a manifest rendition, but after merging it also carries the API url, whose
     * stable key is the id of the hidden side-loaded copy of the same subtitle.
     */
    @Test
    fun select_prefersManifestRendition_overSideLoadedCopyOfTheSameSubtitle() {
        val manifestRow = manifestTrack(
            groupId = "0:rus-rendition",
            trackIndex = 0,
            formatId = "rus-rendition",
            url = "https://api.test/subtitles/29725.srt",
        )
        val candidates = listOf(
            PlayerTextTrack("0:rus-rendition", 0, 0, formatId = "rus-rendition", language = "rus"),
            PlayerTextTrack("1:", 1, 0, formatId = "29725.srt", formatLabel = "29725.srt", language = "rus"),
        )

        assertEquals(candidates[0], selector.select(manifestRow, candidates))
    }

    /**
     * The same collision with a rendition the manifest publishes without an ID: there is no
     * format id to match on, so the old rules fell through to the stable key and selected
     * the side-loaded duplicate. The track group id resolves it unambiguously.
     */
    @Test
    fun select_prefersIdLessManifestRendition_overSideLoadedCopy() {
        val manifestRow = manifestTrack(
            groupId = "0:rus",
            trackIndex = 0,
            formatId = null,
            url = "https://api.test/subtitles/29725.srt",
        )
        val candidates = listOf(
            PlayerTextTrack("0:rus", 0, 0, formatLabel = "Русские", language = "rus"),
            PlayerTextTrack("1:", 1, 0, formatId = "29725.srt", formatLabel = "29725.srt", language = "rus"),
        )

        assertEquals(candidates[0], selector.select(manifestRow, candidates))
    }

    @Test
    fun select_resolvesSideLoadedRow_byStableKey() {
        val sideLoadedRow = SubtitleTrackUIState(
            label = "eng",
            language = "eng",
            url = "https://api.test/subtitles/29726.srt",
            playerTrackGroupId = "1:",
            playerTrackId = "29726.srt",
            playerGroupIndex = 1,
            playerTrackIndex = 0,
        )
        val candidates = listOf(
            PlayerTextTrack("0:rus-rendition", 0, 0, formatId = "rus-rendition", language = "rus"),
            PlayerTextTrack("1:", 1, 0, formatId = "29726.srt", formatLabel = "29726.srt", language = "eng"),
        )

        assertEquals(candidates[1], selector.select(sideLoadedRow, candidates))
    }

    @Test
    fun select_survivesTrackGroupsBeingReordered() {
        val row = manifestTrack(
            groupId = "0:rus-rendition",
            trackIndex = 0,
            formatId = "rus-rendition",
            url = "",
            groupIndex = 0,
        )
        // A later tracks update exposes the same groups in a different order.
        val candidates = listOf(
            PlayerTextTrack("2:eng-rendition", 0, 0, formatId = "eng-rendition", language = "eng"),
            PlayerTextTrack("0:rus-rendition", 1, 0, formatId = "rus-rendition", language = "rus"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_picksCorrectVariant_whenGroupIdsAreBlankAndLanguagesCollide() {
        val row = manifestTrack(
            groupId = "",
            trackIndex = 0,
            formatId = "forced-rus",
            url = "",
            groupIndex = 1,
        )
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, formatId = "full-rus", language = "rus"),
            PlayerTextTrack("", 1, 0, formatId = "forced-rus", language = "rus"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_fallsBackToCoordinates_whenTheManifestExposesNoIdentity() {
        val row = manifestTrack(groupId = "", trackIndex = 0, formatId = null, url = "", groupIndex = 1)
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, language = "rus"),
            PlayerTextTrack("", 1, 0, language = "rus"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_returnsNull_ratherThanGuessing_whenNothingIdentifiesTheTrack() {
        val row = SubtitleTrackUIState(label = "rus", language = "", url = "")
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, language = "rus"),
            PlayerTextTrack("", 1, 0, language = "rus"),
        )

        assertNull(selector.select(row, candidates))
    }

    @Test
    fun select_matchesByLanguage_asLastResort() {
        val row = SubtitleTrackUIState(label = "ukr", language = "ukr", url = "")
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, language = "rus"),
            PlayerTextTrack("", 1, 0, language = "uk"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_returnsNull_whenThePlayerExposesNoTextTracks() {
        assertNull(selector.select(manifestTrack("0:a", 0, "a", ""), emptyList()))
    }

    private fun manifestTrack(
        groupId: String,
        trackIndex: Int,
        formatId: String?,
        url: String,
        groupIndex: Int = 0,
    ) = SubtitleTrackUIState(
        label = "rus",
        language = "rus",
        url = url,
        playerTrackGroupId = groupId,
        playerTrackId = formatId,
        playerTrackUri = "https://cdn.test/rendition.m3u8",
        playerGroupIndex = groupIndex,
        playerTrackIndex = trackIndex,
    )
}
