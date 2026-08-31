package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerVMFocusInitializationTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun onStart_normalEntryPublishesInitialControlsFocus() {
        val vm = startedVM()

        assertTrue(contentState(vm).controlsVisible)
        assertEquals(FocusTarget.Buttons, contentState(vm).controlsFocusTarget)

        vm.onAction(PlayerAction.OnBackPressed)

        assertFalse(contentState(vm).controlsVisible)
        verify(exactly = 0) { router.back(any(), any()) }
    }

    @Test
    fun onStart_resumeDialogKeepsControlsHidden_untilExplicitReveal() {
        every {
            interactor.resolveMedia(any(), any(), any(), any())
        } returns testResolvedMedia.copy(watchingTime = 120)
        coEvery {
            contentStateFactory.build(any(), any(), any(), any(), any(), any())
        } returns testContentState.copy(
            controlsVisible = true,
            controlsFocusTarget = FocusTarget.Buttons,
            resumeDialog = ResumeDialogState(
                savedPosition = 120_000L,
                formattedTime = "2:00",
                episodeInfo = "S1E1",
            ),
        )
        val vm = startedVM()

        assertFalse(contentState(vm).controlsVisible)
        assertEquals(null, contentState(vm).controlsFocusTarget)

        vm.onAction(PlayerAction.ResumeFromPosition)

        assertFalse(contentState(vm).controlsVisible)
        assertEquals(null, contentState(vm).controlsFocusTarget)
    }

    @Test
    fun resumeFromPosition_seeksToSavedPosition_clearsDialog() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            resumeDialog = ResumeDialogState(savedPosition = 120_000L, formattedTime = "2:00", episodeInfo = null),
            isPlaying = false,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.ResumeFromPosition)

        verify { playbackController.seekTo(120_000L) }
        verify { playbackController.play() }
        assertNull(contentState(vm).resumeDialog)
        assertEquals(PlaybackIntent.PlayRequested, contentState(vm).playbackIntent)
    }

    @Test
    fun startFromBeginning_seeksToZero_clearsDialog() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            resumeDialog = ResumeDialogState(savedPosition = 120_000L, formattedTime = "2:00", episodeInfo = null),
            isPlaying = false,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.StartFromBeginning)

        verify { playbackController.seekTo(0) }
        verify { playbackController.play() }
        assertNull(contentState(vm).resumeDialog)
    }
}
