package com.kino.puber.ui.feature.player.vm

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamSource
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
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
    fun build_preservesForcedFlagWithoutMarkingFullSubtitlesDefault() {
        for (isHls in listOf(true, false)) {
            val item = factory.build(
                stream = StreamSource("https://test/video", isHls = isHls),
                subtitles = listOf(
                    SubtitleLink(lang = "eng", url = "https://test/full.vtt", forced = false),
                    SubtitleLink(lang = "eng", url = "https://test/forced.vtt", forced = true),
                    SubtitleLink(lang = "spa", url = "https://test/unknown.vtt", forced = null),
                ),
            )

            assertEquals(
                listOf(0, C.SELECTION_FLAG_FORCED, 0),
                item.localConfiguration?.subtitleConfigurations.orEmpty().map { it.selectionFlags },
            )
        }
    }

    @Test
    fun build_forcedApiFallbackRemainsSelectableAfterMergingWithPlayerTracks() {
        val subtitle = SubtitleLink(lang = "eng", url = "https://test/forced.vtt", forced = true)
        val config = factory.build(StreamSource("https://test/master.m3u8", isHls = true), listOf(subtitle))
            .localConfiguration!!.subtitleConfigurations.single()
        val playerTrack = SubtitleTrackUIState(
            label = config.label.orEmpty(),
            language = config.language.orEmpty(),
            url = "",
            playerTrackId = config.id,
            playerTrackGroupId = "1:forced",
            playerTrackUri = config.uri.toString(),
            playerGroupIndex = 1,
            playerTrackIndex = 0,
            isForced = config.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
        )
        val merged = SubtitleTrackMerger(
            SubtitleLabeler(
                displayLanguageTag = "en",
                aiGeneratedLabel = "AI generated",
                forcedQualifier = "forced",
                variantLabel = { label, ordinal -> "$label ($ordinal)" },
                unknownLabel = { position -> "Track $position" },
            ),
        ).merge(
            listOf(
                SubtitleTrackUIState("Off", "", ""),
                SubtitleTrackUIState("Forced", subtitle.lang, subtitle.url, isForced = subtitle.forced),
            ),
            listOf(playerTrack),
        )
        val candidate = PlayerTextTrack(
            groupId = "1:forced",
            groupIndex = 1,
            trackIndex = 0,
            formatId = config.id,
            formatLabel = config.label,
            language = config.language,
            isForced = playerTrack.isForced,
        )

        assertEquals(
            candidate,
            SubtitleTrackSelector().select(merged.single { it.isForced == true }, listOf(candidate)),
        )
    }

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
