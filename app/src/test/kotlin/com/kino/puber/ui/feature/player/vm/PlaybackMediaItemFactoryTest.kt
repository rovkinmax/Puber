package com.kino.puber.ui.feature.player.vm

import android.app.Application
import androidx.media3.common.MimeTypes
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamSource
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
internal class PlaybackMediaItemFactoryTest {

    private val factory = PlaybackMediaItemFactory()

    @Test
    fun build_preservesStreamAndBuildsStableSubtitleConfigurations() {
        val item = factory.build(
            stream = StreamSource(
                url = "http://127.0.0.1:8080/video.m3u8",
                isHls = true,
            ),
            subtitles = listOf(
                SubtitleLink(
                    lang = "rus",
                    url = "http://127.0.0.1:8080/subtitles/rus.vtt?token=secret#cue",
                ),
                SubtitleLink(
                    lang = "eng",
                    url = "http://127.0.0.1:8080/subtitles/eng.webvtt?expires=123",
                ),
            ),
        )

        assertEquals("http://127.0.0.1:8080/video.m3u8", item.localConfiguration?.uri.toString())
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
        val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
        assertEquals(listOf("rus", "eng"), subtitles.map { it.language })
        assertEquals(listOf("rus.vtt", "eng.webvtt"), subtitles.map { it.id.orEmpty() })
        assertEquals(listOf("rus.vtt", "eng.webvtt"), subtitles.map { it.label })
        assertEquals(listOf(MimeTypes.TEXT_VTT, MimeTypes.TEXT_VTT), subtitles.map { it.mimeType })
        assertEquals(
            listOf(
                "http://127.0.0.1:8080/subtitles/rus.vtt?token=secret#cue",
                "http://127.0.0.1:8080/subtitles/eng.webvtt?expires=123",
            ),
            subtitles.map { it.uri.toString() },
        )
    }

    @Test
    fun build_hlsKeepsApiSubtitleMarkedEmbeddedAsManifestFallback() {
        val item = factory.build(
            stream = StreamSource(url = "https://test/video", isHls = true),
            subtitles = listOf(
                SubtitleLink(lang = "rus", url = "https://test/subtitles/rus.vtt", embed = true),
                SubtitleLink(lang = "eng", url = "https://test/subtitles/eng.vtt", embed = false),
            ),
        )

        val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
        assertEquals(listOf("rus", "eng"), subtitles.map { it.language })
        assertEquals(listOf("rus.vtt", "eng.vtt"), subtitles.map { it.id })
    }

    @Test
    fun build_progressiveSkipsApiSubtitleAlreadyEmbeddedInSourceContainer() {
        val item = factory.build(
            stream = StreamSource(url = "https://hls.test/video.mp4", isHls = false),
            subtitles = listOf(
                SubtitleLink(lang = "rus", url = "https://test/subtitles/rus.vtt", embed = true),
                SubtitleLink(lang = "eng", url = "https://test/subtitles/eng.vtt", embed = false),
            ),
        )

        assertEquals(null, item.localConfiguration?.mimeType)
        val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
        assertEquals(listOf("eng"), subtitles.map { it.language })
        assertEquals(listOf("eng.vtt"), subtitles.map { it.id })
    }

    @Test
    fun subtitleMimeType_handlesQueryFragmentAndFallbackExtensions() {
        assertEquals(MimeTypes.TEXT_VTT, factory.subtitleMimeType("https://test/subtitles/a.VTT?sig=1#x"))
        assertEquals(MimeTypes.TEXT_VTT, factory.subtitleMimeType("https://test/subtitles/a.webvtt"))
        assertEquals(MimeTypes.TEXT_SSA, factory.subtitleMimeType("https://test/subtitles/a.ssa"))
        assertEquals(MimeTypes.APPLICATION_TTML, factory.subtitleMimeType("https://test/subtitles/a.XML#cue"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, factory.subtitleMimeType("https://test/subtitles/a.txt"))
    }
}
