package com.kino.puber.data.api.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleLinkTest {

    @Test
    fun shouldSideLoad_includesExternalSubtitle() {
        val externalSubtitle = SubtitleLink(
            lang = "eng",
            url = "https://cdn.test/subtitle.srt",
        )

        assertTrue(externalSubtitle.shouldSideLoad)
    }

    @Test
    fun shouldSideLoad_excludesEmbeddedSubtitle() {
        val subtitle = SubtitleLink(
            lang = "eng",
            url = "https://cdn.test/subtitle.srt",
            embed = true,
        )

        assertFalse(subtitle.shouldSideLoad)
    }

    @Test
    fun isForced_detectsForcedMarkerInPath_ignoringCaseAndQuery() {
        val subtitle = SubtitleLink(
            lang = "rus",
            url = "https://cdn.test/subtitles/RUS-FORCED.vtt?token=forced-value",
        )

        assertTrue(subtitle.isForced)
    }

    @Test
    fun isForced_doesNotUseQueryOrPartialWordAsMarker() {
        val regularSubtitle = SubtitleLink(
            lang = "rus",
            url = "https://cdn.test/subtitles/russian.vtt?mode=forced",
        )
        val unforcedSubtitle = SubtitleLink(
            lang = "rus",
            url = "https://cdn.test/subtitles/russian-unforced.vtt",
        )

        assertFalse(regularSubtitle.isForced)
        assertFalse(unforcedSubtitle.isForced)
    }

    @Test
    fun isForced_usesApiFlagInsteadOfUrlHeuristic() {
        val forcedSubtitle = SubtitleLink(
            lang = "rus",
            url = "https://cdn.test/subtitles/russian.srt",
            forced = true,
            file = "/a/71/29725.srt",
        )
        val regularSubtitle = SubtitleLink(
            lang = "rus",
            url = "https://cdn.test/subtitles/russian-forced.srt",
            forced = false,
        )

        assertTrue(forcedSubtitle.isForced)
        assertFalse(regularSubtitle.isForced)
    }
}
