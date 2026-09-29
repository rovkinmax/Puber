package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
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
            interactor.saveTrackPreferences(
                42, "en", "English", "en", "subtitle:English Forced", true, "English Forced",
            )
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
            interactor.saveTrackPreferences(
                42, "en", "English", "en", "subtitle:English Forced", true, "English Forced",
            )
        }
        stubPreferredSubtitle("en", "subtitle:English Forced", true, "English Forced")

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
        verify { interactor.saveTrackPreferences(42, "en", "English", null, null, null, null) }
        stubPreferredSubtitle(null, null)

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
        stubPreferredSubtitle(null, null)

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 0) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    @Test
    fun qualityChange_keepsOffAndRetainsPreference_whenOnlyAnotherLanguageExists() {
        val vm = startedWithForcedSubtitle()

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(
            audioTracks,
            0,
            listOf(
                manifestTrack(
                    root = "low",
                    language = "es",
                    forced = true,
                    label = "Spanish Forced",
                    groupId = "subtitle:Spanish Forced",
                ),
            ),
        )

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(match { it.isOff }) }
        verify(exactly = 0) { playbackController.selectSubtitle(match { it.language == "es" }) }

        vm.onAction(PlayerAction.SelectAudioTrack(0))
        verify {
            interactor.saveTrackPreferences(
                42, "en", "English", "en", "subtitle:English Forced", true, "English Forced",
            )
        }
    }

    @Test
    fun qualityChange_keepsOff_whenOnlyForcedVariantCanReplaceSelectedFullVariant() {
        val vm = startedVM()
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("high"))
        vm.onAction(PlayerAction.SelectSubtitle(1))

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(
            audioTracks,
            0,
            listOf(
                manifestTrack(
                    root = "low",
                    language = "en",
                    forced = true,
                    label = "English Full",
                    groupId = "subtitle:English Full",
                ),
            ),
        )

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 0) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    @Test
    fun qualityChange_waitsForDelayedDiscovery_beforeRestoringVariant() {
        val vm = startedWithForcedSubtitle()

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        assertEquals(0, contentState(vm).selectedSubtitleIndex)

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("low"))

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        verify {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("low", true) })
        }
    }

    @Test
    fun qualityChange_restoresPendingVariant_whenReturningFromMissingQuality() {
        val vm = startedWithForcedSubtitle()

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(
            audioTracks,
            0,
            listOf(
                manifestTrack(
                    root = "low",
                    language = "es",
                    forced = true,
                    label = "Spanish Forced",
                    groupId = "subtitle:Spanish Forced",
                ),
            ),
        )
        vm.onAction(PlayerAction.SelectQuality(2))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("high"))

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 2) {
            playbackController.selectSubtitle(match { it.playerTrackUri == subtitleUri("high", true) })
        }
    }

    @Test
    fun qualityChange_keepsOff_whenReusedIdentityIsAmbiguousWithinSameSemanticVariant() {
        val vm = startedVM()
        val selected = manifestTrack(
            root = "high",
            language = "en",
            forced = false,
            label = "English Commentary",
            groupId = "reused-id",
        )
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, listOf(selected))
        vm.onAction(PlayerAction.SelectSubtitle(1))

        vm.onAction(PlayerAction.SelectQuality(1))
        callbackSlot.captured.onTracksUpdated(
            audioTracks,
            0,
            listOf(
                manifestTrack("low-a", "en", false, "", "reused-id"),
                manifestTrack("low-b", "en", false, "", "reused-id"),
            ),
        )

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 0) {
            playbackController.selectSubtitle(match { it.playerTrackUri?.contains("low-") == true })
        }
    }

    private fun startedWithForcedSubtitle(): PlayerVM {
        val vm = startedVM()
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks("high"))
        vm.onAction(PlayerAction.SelectSubtitle(2))
        stubPreferredSubtitle("en", subtitleUri("high", true), true, "English Forced")
        return vm
    }

    private val audioTracks = listOf(AudioTrackUIState(0, "English", "en"))

    private fun manifestTracks(root: String) = listOf(false, true).mapIndexed { index, forced ->
        val label = if (forced) "English Forced" else "English Full"
        manifestTrack(root, "en", forced, label, "subtitle:$label", index)
    }

    private fun manifestTrack(
        root: String,
        language: String,
        forced: Boolean,
        label: String,
        groupId: String,
        groupIndex: Int = 0,
    ) = SubtitleTrackUIState(
        label = label,
        language = language,
        url = "",
        isForced = forced,
        descriptiveLabel = label.takeIf { it.isNotEmpty() },
        // Some Media3 sources expose the same format id for all subtitle groups.
        playerTrackId = "0:",
        playerTrackGroupId = groupId,
        playerTrackUri = subtitleUri(root, forced),
        playerGroupIndex = groupIndex,
        playerTrackIndex = 0,
    )

    private fun subtitleUri(root: String, forced: Boolean): String =
        "https://cdn.test/$root/subtitle_${if (forced) "forced" else "full"}.m3u8"
}
