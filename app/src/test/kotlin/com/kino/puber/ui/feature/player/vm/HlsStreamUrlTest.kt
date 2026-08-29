package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HlsStreamUrlTest {

    @Test
    fun isHlsStreamUrl_detectsPlaylistExtension_evenWithQueryAndFragment() {
        assertTrue("https://cdn.example/video/master.m3u8".isHlsStreamUrl())
        assertTrue("https://cdn.example/video/master.M3U8?token=abc#t=10".isHlsStreamUrl())
    }

    @Test
    fun isHlsStreamUrl_detectsHlsPathSegment() {
        assertTrue("https://cdn.example/a/hls/index".isHlsStreamUrl())
        assertTrue("https://cdn.example/a/hls4/index".isHlsStreamUrl())
    }

    @Test
    fun isHlsStreamUrl_ignoresHostAndQueryMatches() {
        assertFalse("https://hls.cdn.example/video/file.mp4".isHlsStreamUrl())
        assertFalse("https://cdn.example/video/file.mp4?profile=hls".isHlsStreamUrl())
        assertFalse("https://cdn.example/video/file.mp4#hls".isHlsStreamUrl())
    }

    @Test
    fun isHlsStreamUrl_ignoresPartialWordMatchesInPath() {
        assertFalse("https://cdn.example/hlsx/file.mp4".isHlsStreamUrl())
        assertFalse("https://cdn.example/nothls/file.mp4".isHlsStreamUrl())
    }

    @Test
    fun isHlsStreamUrl_handlesMalformedUrlsWithoutThrowing() {
        assertTrue("https://cdn.example/a b/master.m3u8".isHlsStreamUrl())
        assertFalse("not a url".isHlsStreamUrl())
        assertFalse("".isHlsStreamUrl())
    }
}
