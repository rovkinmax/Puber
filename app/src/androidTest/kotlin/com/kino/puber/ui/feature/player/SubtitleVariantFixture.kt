package com.kino.puber.ui.feature.player

import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.profile.PlayerTestControl

internal const val FULL_SUBTITLE_MANIFEST_LABEL = "English Full"
internal const val FORCED_SUBTITLE_MANIFEST_LABEL = "English Forced"
internal const val FULL_SUBTITLE_CUE = "Full subtitle cue"
internal const val FORCED_SUBTITLE_CUE = "Forced subtitle cue"

internal fun subtitleVariantMaster(query: String? = null): String {
    val querySuffix = query?.let { "?$it" }.orEmpty()
    return """
    #EXTM3U
    #EXT-X-VERSION:3
    #EXT-X-INDEPENDENT-SEGMENTS
    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio_english.m3u8"
    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Español",LANGUAGE="es",DEFAULT=NO,AUTOSELECT=YES,URI="audio_spanish.m3u8"
    #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="$FULL_SUBTITLE_MANIFEST_LABEL",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,FORCED=NO,URI="subtitle_full.m3u8$querySuffix"
    #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="$FORCED_SUBTITLE_MANIFEST_LABEL",LANGUAGE="en",DEFAULT=NO,AUTOSELECT=YES,FORCED=YES,URI="subtitle_forced.m3u8$querySuffix"
    #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio",SUBTITLES="subs"
    video_360.m3u8
    #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio",SUBTITLES="subs"
    video_720.m3u8
    """.trimIndent()
}

internal fun PlayerTestControl.subtitleVariantRoutes(
    root: String,
    query: String? = null,
): List<HermeticRoute> {
    val idPrefix = root.trim('/').replace('/', '-')
    val querySuffix = query?.let { "?$it" }.orEmpty()
    return listOf(
        route(
            id = "$idPrefix:subtitle-full-playlist",
            path = "$root/subtitle_full.m3u8",
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(
                body = subtitlePlaylist("subtitle_full.vtt$querySuffix"),
                contentType = HLS_CONTENT_TYPE,
            ),
        ),
        route(
            id = "$idPrefix:subtitle-forced-playlist",
            path = "$root/subtitle_forced.m3u8",
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(
                body = subtitlePlaylist("subtitle_forced.vtt$querySuffix"),
                contentType = HLS_CONTENT_TYPE,
            ),
        ),
        route(
            id = "$idPrefix:subtitle-full-cue",
            path = "$root/subtitle_full.vtt",
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(
                body = subtitleCue(FULL_SUBTITLE_CUE),
                contentType = "text/vtt",
            ),
        ),
        route(
            id = "$idPrefix:subtitle-forced-cue",
            path = "$root/subtitle_forced.vtt",
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(
                body = subtitleCue(FORCED_SUBTITLE_CUE),
                contentType = "text/vtt",
            ),
        ),
    )
}

private fun subtitlePlaylist(cueFile: String): String =
    """
    #EXTM3U
    #EXT-X-VERSION:3
    #EXT-X-TARGETDURATION:4
    #EXT-X-MEDIA-SEQUENCE:0
    #EXTINF:4.000,
    $cueFile
    #EXT-X-ENDLIST
    """.trimIndent()

private fun subtitleCue(text: String): String =
    """
    WEBVTT

    00:00:00.000 --> 00:00:04.000
    $text
    """.trimIndent()

private const val HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"
