package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubtitleTrackIdentityTest {

    @Test
    fun stableSubtitleKey_excludesSignedQueryAndFragment() {
        assertEquals(
            "episode-1.vtt",
            "https://cdn.example/subtitles/episode-1.vtt?token=secret#captions".stableSubtitleKey(),
        )
    }

    @Test
    fun stableSubtitleKey_keepsDistinctSubtitlePaths() {
        assertEquals(
            "episode-1.vtt",
            "https://cdn.example/subtitles/episode-1.vtt?expires=1".stableSubtitleKey(),
        )
        assertEquals(
            "episode-1-forced.vtt",
            "https://cdn.example/subtitles/episode-1-forced.vtt?expires=1".stableSubtitleKey(),
        )
    }

    @Test
    fun stableSubtitleKey_handlesEmptyAndNonStandardUrls() {
        assertEquals("", "".stableSubtitleKey())
        assertEquals("captions.vtt", "captions.vtt?token=1#cue".stableSubtitleKey())
    }
}
