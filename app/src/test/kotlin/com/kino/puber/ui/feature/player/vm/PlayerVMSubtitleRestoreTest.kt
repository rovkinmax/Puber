package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerVMSubtitleRestoreTest : PlayerVMTestFixture() {
    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun qualityChange_restoresForcedRow_whenUrlsAndTrackOrderChange() {
        val vm = startedWithForcedSubtitle()
        vm.onAction(PlayerAction.SelectQuality(1))
        val newTracks = manifestTracks("low").reversed().mapIndexed { index, track ->
            track.copy(playerGroupIndex = index)
        }

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, newTracks)

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        val selected = contentState(vm).subtitleTracks[2]
        assertEquals("subtitle:English Forced", selected.playerTrackGroupId)
        assertEquals(0, selected.playerGroupIndex)
        verify { playbackController.selectSubtitle(selected) }
        vm.onAction(PlayerAction.SelectAudioTrack(0))
        verify {
            interactor.saveTrackPreferences(42, "en", "English", "en", "subtitle:English Forced")
        }
    }

    @Test
    fun rapidQualityChanges_keepVariantIdentity_untilNewTracksArrive() {
        val vm = startedWithForcedSubtitle()

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        vm.onAction(PlayerAction.SelectQuality(2))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        verify {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    @Test
    fun recreation_restoresSavedRendition_afterQualityAndAudioChanges() {
        val vm = startedWithForcedSubtitle()
        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))
        vm.onAction(PlayerAction.SelectAudioTrack(0))
        verify {
            interactor.saveTrackPreferences(42, "en", "English", "en", "subtitle:English Forced")
        }
        every { interactor.getPreferredSubtitleUrl(42) } returns "subtitle:English Forced"

        val recreated = startedVM()
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("high"))

        assertEquals(2, contentState(recreated).selectedSubtitleIndex)
        verify(exactly = 2) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("high", true) })
        }
    }

    @Test
    fun playerRestartAfterQualityChange_restoresCurrentVariantDespiteOldSavedUrl() {
        val vm = startedWithForcedSubtitle()
        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        vm.onAction(PlayerAction.ToggleFastDns)
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 2) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    @Test
    fun offDuringQualityChange_cancelsPendingVariantAndClearsPreference() {
        val vm = startedWithForcedSubtitle()
        vm.onAction(PlayerAction.SelectQuality(1))
        vm.onAction(PlayerAction.SelectSubtitle(0))
        verify { interactor.saveTrackPreferences(42, "en", "English", null, null) }
        every { interactor.getPreferredSubtitleLang(42) } returns null
        every { interactor.getPreferredSubtitleUrl(42) } returns null

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 0) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    @Test
    fun qualityChange_doesNotRestoreOldVariant_afterUserSelectsOff() {
        val vm = startedWithForcedSubtitle()
        vm.onAction(PlayerAction.SelectSubtitle(0))
        every { interactor.getPreferredSubtitleLang(42) } returns null
        every { interactor.getPreferredSubtitleUrl(42) } returns null

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 0) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    private fun startedWithForcedSubtitle(): PlayerVM {
        val vm = startedVM()
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("high"))
        vm.onAction(PlayerAction.SelectSubtitle(2))
        every { interactor.getPreferredSubtitleLang(42) } returns "en"
        every { interactor.getPreferredSubtitleUrl(42) } returns subtitleUri("high", true)
        return vm
    }

    private val audioTracks = listOf(AudioTrackUIState(0, "English", "en"))

    private fun manifestTracks(root: String) = listOf(false, true).mapIndexed { index, forced ->
        val label = if (forced) "English Forced" else "English Full"
        SubtitleTrackUIState(
            label = label,
            language = "en",
            url = "",
            isForced = forced,
            // Some Media3 sources expose the same format id for all subtitle groups.
            playerTrackId = "0:",
            playerTrackGroupId = "subtitle:$label",
            playerTrackUri = subtitleUri(root, forced),
            playerGroupIndex = index,
            playerTrackIndex = 0,
        )
    }

    private fun subtitleUri(root: String, forced: Boolean): String =
        "https://cdn.test/$root/subtitle_${if (forced) "forced" else "full"}.m3u8"
}
