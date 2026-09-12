package com.kino.puber.ui.feature.player.vm

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.player.StreamSource
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
internal class PlaybackControllerCallbackGenerationTest {

    @Test
    fun rapidSwitches_rejectSupersededProductionListenersAndAdmitCurrentCallbacks() {
        val context = RuntimeEnvironment.getApplication()
        val player = mockk<ExoPlayer>(relaxed = true)
        val addedListeners = mutableListOf<Player.Listener>()
        val removedListeners = mutableListOf<Player.Listener>()
        val selectedStreams = mutableListOf<MediaItem>()
        var currentPosition = C_POSITION_MS
        var duration = DURATION_MS
        var bufferedPosition = C_BUFFERED_POSITION_MS
        var isPlaying = true
        var playWhenReady = true
        var playbackState = Player.STATE_READY
        var playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        var trackSelectionParameters = mockk<TrackSelectionParameters>(relaxed = true)

        every { player.addListener(capture(addedListeners)) } just runs
        every { player.removeListener(capture(removedListeners)) } just runs
        every { player.setMediaItem(capture(selectedStreams)) } just runs
        every { player.currentPosition } answers { currentPosition }
        every { player.duration } answers { duration }
        every { player.bufferedPosition } answers { bufferedPosition }
        every { player.isPlaying } answers { isPlaying }
        every { player.playWhenReady } answers { playWhenReady }
        every { player.playWhenReady = any() } answers { playWhenReady = firstArg() }
        every { player.playbackState } answers { playbackState }
        every { player.playbackSuppressionReason } answers { playbackSuppressionReason }
        every { player.trackSelectionParameters } answers { trackSelectionParameters }
        every { player.trackSelectionParameters = any() } answers {
            trackSelectionParameters = firstArg()
        }
        every { player.currentTracks } returns Tracks.EMPTY

        val callback = RecordingCallback()
        val controller = PlaybackController(
            context = context,
            okHttpClient = OkHttpClient(),
            mediaCache = mockk<Cache>(relaxed = true),
            playerPreferencesRepository = PlayerPreferencesRepository(context),
        )
        controller.setCallback(callback)
        controller.setPrivateField("exoPlayer", player)
        controller.setPrivateField("dataSourceFactory", mockk<DataSource.Factory>(relaxed = true))

        controller.switchStream(progressiveStream(STREAM_A), subtitles = null)
        val listenerA = addedListeners.single()
        controller.switchStream(progressiveStream(STREAM_B), subtitles = null)
        val listenerB = addedListeners.last()
        controller.switchStream(progressiveStream(STREAM_C), subtitles = null)
        val listenerC = addedListeners.last()

        assertEquals(listOf(STREAM_A, STREAM_B, STREAM_C), selectedStreams.map(::streamUri))
        assertEquals(listOf(listenerA, listenerB), removedListeners)
        assertEquals(3, addedListeners.distinct().size)

        callback.clear()
        listenerC.onPlaybackStateChanged(Player.STATE_READY)
        val admittedCState = callback.snapshots.single()
        assertEquals(C_POSITION_MS, admittedCState.position)
        assertEquals(PlaybackIntent.PlayRequested, admittedCState.intent)
        assertEquals(false, admittedCState.isBuffering)

        repeat(POST_C_STABILITY_DELIVERIES) {
            listenerA.onPlayerError(playbackError(STALE_A_ERROR))
            currentPosition = B_POSITION_MS
            bufferedPosition = B_BUFFERED_POSITION_MS
            isPlaying = false
            playWhenReady = false
            playbackState = Player.STATE_BUFFERING
            listenerB.onPlaybackStateChanged(Player.STATE_BUFFERING)
            currentPosition = C_POSITION_MS
            bufferedPosition = C_BUFFERED_POSITION_MS
            isPlaying = true
            playWhenReady = true
            playbackState = Player.STATE_READY
        }

        assertEquals(listOf(admittedCState), callback.snapshots)
        assertEquals(emptyList<String>(), callback.errors)
        assertEquals(STREAM_C, streamUri(selectedStreams.last()))
        assertEquals(C_POSITION_MS, controller.currentPosition)
        assertEquals(PlaybackIntent.PlayRequested, controller.playbackIntent)
        assertEquals(true, controller.isPlaying)
        assertSame(listenerC, addedListeners.last())

        listenerC.onPlaybackStateChanged(Player.STATE_READY)
        listenerC.onPlayerError(playbackError(CURRENT_C_ERROR))

        assertEquals(listOf(admittedCState, admittedCState), callback.snapshots)
        assertEquals(listOf(CURRENT_C_ERROR), callback.errors)
    }

    private fun playbackError(message: String) = PlaybackException(
        message,
        null,
        PlaybackException.ERROR_CODE_UNSPECIFIED,
    )

    private fun streamUri(mediaItem: MediaItem): String {
        return mediaItem.localConfiguration?.uri.toString()
    }

    private fun progressiveStream(url: String) = StreamSource(url = url, isHls = false)

    private fun PlaybackController.setPrivateField(name: String, value: Any) {
        PlaybackController::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    private class RecordingCallback : PlaybackControl.Callback {
        val snapshots = mutableListOf<PlaybackSnapshot>()
        val errors = mutableListOf<String>()

        override fun onPlaybackStateChanged(snapshot: PlaybackSnapshot) {
            snapshots += snapshot
        }

        override fun onPlaybackEnded() = Unit

        override fun onTracksUpdated(
            audioTracks: List<AudioTrackUIState>,
            selectedIndex: Int,
            subtitleTracks: List<SubtitleTrackUIState>,
        ) = Unit

        override fun onError(message: String) {
            errors += message
        }

        fun clear() {
            snapshots.clear()
            errors.clear()
        }
    }

    private companion object {
        const val STREAM_A = "http://127.0.0.1/quality-a.mp4"
        const val STREAM_B = "http://127.0.0.1/quality-b.mp4"
        const val STREAM_C = "http://127.0.0.1/quality-c.mp4"
        const val STALE_A_ERROR = "Stale A error"
        const val CURRENT_C_ERROR = "Current C error"
        const val C_POSITION_MS = 30_000L
        const val C_BUFFERED_POSITION_MS = 90_000L
        const val B_POSITION_MS = 5_000L
        const val B_BUFFERED_POSITION_MS = 10_000L
        const val DURATION_MS = 2_400_000L
        const val POST_C_STABILITY_DELIVERIES = 3
    }
}
