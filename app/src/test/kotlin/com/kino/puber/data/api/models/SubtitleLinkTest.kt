package com.kino.puber.data.api.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleLinkTest {

    @Test
    fun shouldSideLoad_sideLoadsEveryApiSubtitleForHls() {
        val url = "https://cdn.test/subtitle.srt"

        assertTrue(SubtitleLink(lang = "eng", url = url).shouldSideLoad(isHls = true))
        assertTrue(SubtitleLink(lang = "eng", url = url, embed = false).shouldSideLoad(isHls = true))
        assertTrue(SubtitleLink(lang = "eng", url = url, embed = true).shouldSideLoad(isHls = true))
    }

    @Test
    fun shouldSideLoad_skipsEmbeddedApiSubtitleForProgressiveSource() {
        val url = "https://cdn.test/subtitle.srt"

        assertTrue(SubtitleLink(lang = "eng", url = url).shouldSideLoad(isHls = false))
        assertTrue(SubtitleLink(lang = "eng", url = url, embed = false).shouldSideLoad(isHls = false))
        assertFalse(SubtitleLink(lang = "eng", url = url, embed = true).shouldSideLoad(isHls = false))
    }
}
