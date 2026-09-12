package com.kino.puber.ui.feature.player.vm

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.player.StreamSource
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
internal class PlaybackControllerSubtitleDefaultsTest {
    @Test
    fun prepare_keepsDefaultManifestSubtitleOff_afterPlayerRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val controller = PlaybackController(
            context, OkHttpClient(), mockk<Cache>(relaxed = true), PlayerPreferencesRepository(context),
        )
        val renderer = mockk<RendererCapabilities>(relaxed = true) {
            every { trackType } returns C.TRACK_TYPE_TEXT
            every { supportsFormat(any()) } returns C.FORMAT_HANDLED
        }
        val format = Format.Builder()
            .setId("subs:English Full")
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val groups = TrackGroupArray(TrackGroup("subtitle:English Full", format))
        try {
            controller.prepare(StreamSource("file:///unused.mp4", isHls = false), null, null)
            controller.selectSubtitle(SubtitleTrackUIState("Off", "", ""))
            controller.release()
            controller.prepare(StreamSource("file:///unused.mp4", isHls = false), null, null)

            val selector = PlaybackController::class.java.getDeclaredField("trackSelector").run {
                isAccessible = true
                get(controller) as DefaultTrackSelector
            }
            val result = selector.selectTracks(
                arrayOf(renderer), groups, MediaSource.MediaPeriodId(Any()), Timeline.EMPTY,
            )
            assertNull(result.selections[0])
        } finally {
            controller.release()
        }
    }
}
