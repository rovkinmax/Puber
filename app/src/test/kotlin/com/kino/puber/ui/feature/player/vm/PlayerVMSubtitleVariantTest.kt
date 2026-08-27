package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerVMSubtitleVariantTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun tracksUpdated_restoresForcedManifestSubtitleBySavedIdentity() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "hls-russian-forced"
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTracks = listOf(
            testSubtitleTracks.first().copy(
                index = 1,
                label = "Russian full",
                language = "ru",
                isForced = false,
                playerTrackId = "hls-russian-full",
                playerGroupIndex = 0,
                playerTrackIndex = 0,
            ),
            testSubtitleTracks.first().copy(
                index = 2,
                label = "Russian forced",
                language = "ru",
                isForced = true,
                playerTrackId = "hls-russian-forced",
                playerGroupIndex = 1,
                playerTrackIndex = 0,
            ),
        )

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        verify(exactly = 0) { playbackController.selectSubtitle(any()) }

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks)

        val selectedTrack = contentState(vm).subtitleTracks[4]
        assertEquals("hls-russian-forced", selectedTrack.playerTrackId)
        assertEquals(4, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(selectedTrack) }
    }
}
