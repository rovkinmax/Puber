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

internal class PlayerVMSubtitleVariantTest : PlayerVMTestFixture() {

    companion object {
        private const val EMPTY_TRACK_UPDATES = 11

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun tracksUpdated_preservesSelectedLanguageLessTrackOnRepeatedAndReorderedCallbacks() {
        val vm = startedVM()
        val unknown = SubtitleTrackUIState(
            label = "Unknown",
            language = "",
            url = "",
            playerTrackId = "unknown",
            playerTrackGroupId = "unknown-group",
            playerGroupIndex = 0,
            playerTrackIndex = 0,
            isForced = false,
        )
        val other = unknown.copy(
            playerTrackId = "other",
            playerTrackGroupId = "other-group",
            playerGroupIndex = 1,
        )
        callbackSlot.captured.onTracksUpdated(emptyList(), 0, listOf(unknown, other))
        vm.onAction(PlayerAction.SelectSubtitle(1))

        for (tracks in listOf(listOf(unknown, other), listOf(other, unknown))) {
            callbackSlot.captured.onTracksUpdated(emptyList(), 0, tracks)
            val content = contentState(vm)
            assertEquals("unknown", content.subtitleTracks[content.selectedSubtitleIndex].playerTrackId)
        }

        vm.onAction(PlayerAction.SelectSubtitle(0))
        callbackSlot.captured.onTracksUpdated(emptyList(), 0, listOf(other, unknown))
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_addsAndSelectsManifestOnlySubtitle_withLanguagePreference() {
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTrack = testSubtitleTracks.first().copy(
            label = "Ukrainian HLS",
            language = "uk",
            playerTrackId = "hls-ukrainian",
            playerTrackUri = "https://cdn.test/subtitle/ukrainian.vtt",
            playerGroupIndex = 0,
            playerTrackIndex = 0,
        )

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, listOf(manifestTrack))
        vm.onAction(PlayerAction.SelectSubtitle(1))

        val selectedTrack = contentState(vm).subtitleTracks[1]
        assertEquals("uk", selectedTrack.language)
        assertEquals("hls-ukrainian", selectedTrack.playerTrackId)
        verify { playbackController.selectSubtitle(selectedTrack) }
        verify {
            interactor.saveTrackPreferences(
                42,
                "eng",
                "English",
                "uk",
                "https://cdn.test/subtitle/ukrainian.vtt",
                null,
                null,
            )
        }
    }

    @Test
    fun tracksUpdated_defersUrlLessLanguagePreference_untilManifestTracksAppear() {
        stubPreferredSubtitle("ukr", "")
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTrack = testSubtitleTracks.first().copy(
            label = "Ukrainian HLS",
            language = "uk",
            playerTrackId = "hls-ukrainian",
            playerGroupIndex = 0,
            playerTrackIndex = 0,
        )

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        verify(exactly = 0) { playbackController.selectSubtitle(any()) }

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, listOf(manifestTrack))

        val selectedTrack = contentState(vm).subtitleTracks[1]
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
        assertEquals("hls-ukrainian", selectedTrack.playerTrackId)
        verify { playbackController.selectSubtitle(selectedTrack) }
    }

    @Test
    fun tracksUpdated_restoresForcedManifestSubtitleBySavedIdentity() {
        stubPreferredSubtitle("rus", "hls-russian-forced")
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTracks = listOf(
            testSubtitleTracks.first().copy(
                label = "Russian full",
                language = "ru",
                isForced = false,
                playerTrackId = "hls-russian-full",
                playerGroupIndex = 0,
                playerTrackIndex = 0,
            ),
            testSubtitleTracks.first().copy(
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

        val selectedTrack = contentState(vm).subtitleTracks[2]
        assertEquals("hls-russian-forced", selectedTrack.playerTrackId)
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(selectedTrack) }
    }

    @Test
    fun tracksUpdated_keepsSelectedVariant_whenSelectedFormatIdDisappears() {
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTracks = listOf(
            manifestTrack("English 1", "manifest-eng-1", groupIndex = 5),
            manifestTrack("English 2", "manifest-eng-2", groupIndex = 6),
            manifestTrack("English 3", "manifest-eng-3", groupIndex = 7),
        )
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks)
        vm.onAction(PlayerAction.SelectSubtitle(2))

        callbackSlot.captured.onTracksUpdated(
            audioTracks,
            0,
            manifestTracks.map { it.copy(playerTrackId = null, playerTrackUri = null) },
        )

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        assertEquals(6, contentState(vm).subtitleTracks[2].playerGroupIndex)
    }

    @Test
    fun tracksUpdated_keepsSelectedVariant_duringTransientEmptyTrackUpdate() {
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTracks = listOf(
            manifestTrack("English 1", "manifest-eng-1", groupIndex = 5),
            manifestTrack("English 2", "manifest-eng-2", groupIndex = 6),
        )
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks)
        vm.onAction(PlayerAction.SelectSubtitle(2))

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())

        assertEquals(2, contentState(vm).selectedSubtitleIndex)
        assertEquals("manifest-eng-2", contentState(vm).subtitleTracks[2].playerTrackId)
    }

    @Test
    fun tracksUpdated_keepsAudioTracks_whenUpdateCarriesOnlyTextTracks() {
        val vm = startedVM()
        val audioTracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Russian", "rus"),
        )
        callbackSlot.captured.onTracksUpdated(audioTracks, 1, testDiscoveredSubtitleTracks)

        callbackSlot.captured.onTracksUpdated(emptyList(), 0, testDiscoveredSubtitleTracks)

        assertEquals(audioTracks, contentState(vm).audioTracks)
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
    }

    @Test
    fun tracksUpdated_restoresSubtitle_whenTrackAppearsAfterElevenEmptyUpdates() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        stubPreferredSubtitle("rus", "hls-russian-forced")
        val vm = startedVM()
        val audioTracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Russian", "rus"),
        )
        val manifestTracks = listOf(
            manifestTrack("Russian full", "hls-russian-full", groupIndex = 0),
            manifestTrack("Russian forced", "hls-russian-forced", groupIndex = 1)
                .copy(language = "ru", isForced = true),
        )

        repeat(EMPTY_TRACK_UPDATES) {
            callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        }
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks)

        verify(exactly = 1) { playbackController.selectAudioTrack(any()) }
        verify(exactly = 1) {
            playbackController.selectSubtitle(match { it.playerTrackId == "hls-russian-forced" })
        }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun retryPlayback_restoresSelectedAudioAndExactSubtitleVariant() {
        val vm = startedVM()
        val audioTracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Spanish", "es"),
        )
        val manifestTracks = listOf(
            manifestTrack("English full", "hls-english-full", groupIndex = 0),
            manifestTrack("English forced", "hls-english-forced", groupIndex = 1)
                .copy(isForced = true),
        )
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks)
        vm.onAction(PlayerAction.SelectAudioTrack(1))
        vm.onAction(PlayerAction.SelectSubtitle(2))
        every { interactor.getPreferredAudioLabel(42) } returns "Spanish"
        every { interactor.getPreferredAudioLang(42) } returns "es"
        stubPreferredSubtitle("en", "hls-english-forced")

        callbackSlot.captured.onError("Network error")
        vm.onAction(PlayerAction.RetryPlayback)
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, manifestTracks)

        verify(exactly = 2) { playbackController.selectAudioTrack(1) }
        verify(exactly = 2) {
            playbackController.selectSubtitle(match { it.playerTrackId == "hls-english-forced" })
        }
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_appliesAudioRestoreOnce_andLetsLaterUserChoiceStand() {
        every { interactor.getPreferredAudioLang(42) } returns "eng"
        stubPreferredSubtitle("rus", "")
        val vm = startedVM()
        val audioTracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Russian", "rus"),
        )

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        vm.onAction(PlayerAction.SelectAudioTrack(1))
        callbackSlot.captured.onTracksUpdated(audioTracks, 1, emptyList())

        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
        // Restored once on the first update, never re-applied over the user's choice.
        verify(exactly = 1) { playbackController.selectAudioTrack(0) }
    }

    @Test
    fun audioSelection_keepsStoredSubtitlePreference_whileSubtitleTracksAreUnknown() {
        stubPreferredSubtitle("rus", "https://api.test/subtitles/rus.srt")
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())

        vm.onAction(PlayerAction.SelectAudioTrack(0))

        verify {
            interactor.saveTrackPreferences(
                42,
                "eng",
                "English",
                "rus",
                "https://api.test/subtitles/rus.srt",
                null,
                null,
            )
        }
    }

    @Test
    fun subtitleSelection_persistsOffChoice_onceSubtitleTracksAreKnown() {
        stubPreferredSubtitle("rus", "https://api.test/subtitles/rus.srt")
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        callbackSlot.captured.onTracksUpdated(audioTracks, 0, testDiscoveredSubtitleTracks)

        vm.onAction(PlayerAction.SelectSubtitle(0))

        verify { interactor.saveTrackPreferences(42, "eng", "English", null, null, null, null) }
    }

    private fun manifestTrack(
        label: String,
        id: String,
        groupIndex: Int,
    ) = SubtitleTrackUIState(
        label = label,
        language = "en",
        url = "",
        playerTrackId = id,
        playerTrackUri = "https://cdn.test/subtitle/$id.vtt",
        playerGroupIndex = groupIndex,
        playerTrackIndex = 0,
    )
}
