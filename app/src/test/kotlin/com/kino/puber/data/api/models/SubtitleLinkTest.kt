package com.kino.puber.data.api.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleLinkTest {

    @Test
    fun shouldSideLoad_coversEverythingButEmbeddedSubtitles() {
        val url = "https://cdn.test/subtitle.srt"

        assertTrue(SubtitleLink(lang = "eng", url = url).shouldSideLoad)
        assertTrue(SubtitleLink(lang = "eng", url = url, embed = false).shouldSideLoad)
        assertFalse(SubtitleLink(lang = "eng", url = url, embed = true).shouldSideLoad)
    }
}
