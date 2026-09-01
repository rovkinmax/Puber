package com.kino.puber.ui.feature.player.component

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.PlayerInstrumentationTestCase
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.vm.PlaybackIntent
import com.kino.puber.ui.feature.player.vm.PlaybackStatePolicy
import com.kino.puber.ui.feature.player.vm.withPlaybackSnapshot
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class PlayerVideoSurfaceTest : PlayerInstrumentationTestCase() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playbackSnapshots_reachAttachedWindow_andClearWithPlayerOnRelease() = run {
        val contentState = mutableStateOf(content())
        val surfaceVisible = mutableStateOf(true)
        val playerViewReference = AtomicReference<PlayerView>()
        var player: ExoPlayer? = null

        step("Attach the production video surface to an idle player") {
            composeRule.setContent {
                if (surfaceVisible.value) {
                    MaterialTheme {
                        PlayerVideoSurface(
                            content = contentState.value,
                            exoPlayer = { player },
                            playerViewFactory = { context ->
                                player = player ?: ExoPlayer.Builder(context).build()
                                PlayerView(context).also(playerViewReference::set)
                            },
                        )
                    }
                }
            }

            composeRule.waitUntil { playerViewReference.get()?.isAttachedToWindow == true }
            composeRule.runOnIdle {
                val playerView = playerViewReference.get()
                assertFalse(playerView.keepScreenOn)
                assertSame(player, playerView.player)
                assertWindowKeepScreenOn(expected = false)
            }
        }

        step("Acquire keep-screen-on ownership while READY and playing") {
            publish(contentState, playbackState = Player.STATE_READY, playWhenReady = true)
            assertAttachedKeepScreenOn(playerViewReference, expected = true)
        }

        step("Retain keep-screen-on ownership while actively buffering") {
            publish(contentState, playbackState = Player.STATE_BUFFERING, playWhenReady = true)
            assertAttachedKeepScreenOn(playerViewReference, expected = true)
        }

        step("Release keep-screen-on ownership when playback pauses") {
            publish(contentState, playbackState = Player.STATE_READY, playWhenReady = false)
            assertAttachedKeepScreenOn(playerViewReference, expected = false)
        }

        step("Keep ownership released while playback is suppressed") {
            publish(
                contentState = contentState,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            )
            assertAttachedKeepScreenOn(playerViewReference, expected = false)
        }

        step("Detach the released surface and clear player and window ownership") {
            composeRule.runOnIdle {
                surfaceVisible.value = false
            }
            composeRule.waitUntil {
                playerViewReference.get().player == null &&
                    !playerViewReference.get().keepScreenOn
            }
            composeRule.runOnIdle {
                val playerView = playerViewReference.get()
                assertFalse(playerView.keepScreenOn)
                assertNull(playerView.player)
                assertWindowKeepScreenOn(expected = false)
                player?.release()
                player = null
            }
        }
    }

    @Test
    fun activeBuffering_reacquiresWindowOwnershipAtPlayerViewAttachBoundary() = run {
        val contentState = mutableStateOf(
            content().withPlaybackSnapshot(
                PlaybackStatePolicy.derive(
                    isPlaying = false,
                    playWhenReady = true,
                    playbackState = Player.STATE_BUFFERING,
                    suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                ),
            ),
        )
        val playerViewReference = AtomicReference<PlayerView>()
        var player: ExoPlayer? = null

        step("Attach an actively buffering surface after the view clears the window flag") {
            composeRule.setContent {
                MaterialTheme {
                    PlayerVideoSurface(
                        content = contentState.value,
                        exoPlayer = { player },
                        playerViewFactory = { context ->
                            player = ExoPlayer.Builder(context).build()
                            object : PlayerView(context) {
                                override fun onAttachedToWindow() {
                                    super.onAttachedToWindow()
                                    composeRule.activity.window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                    )
                                }
                            }.also(playerViewReference::set)
                        },
                    )
                }
            }
        }

        step("Reacquire view and window ownership at the attach boundary") {
            composeRule.waitUntil { playerViewReference.get()?.isAttachedToWindow == true }
            composeRule.waitUntil {
                composeRule.activity.window.attributes.flags and
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            }
            composeRule.runOnIdle {
                assertTrue(playerViewReference.get().keepScreenOn)
                assertWindowKeepScreenOn(expected = true)
            }
        }

        step("Detach the player before releasing the real ExoPlayer") {
            composeRule.runOnIdle {
                playerViewReference.get().player = null
                player?.release()
                player = null
            }
        }
    }

    @Test
    fun preExistingWindowKeepScreenOn_survivesPlaybackTransitionsAndRelease() = run {
        val contentState = mutableStateOf(content())
        val surfaceVisible = mutableStateOf(true)
        val playerViewReference = AtomicReference<PlayerView>()
        var player: ExoPlayer? = null

        step("Establish pre-existing window keep-screen-on ownership") {
            composeRule.runOnIdle {
                composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                assertWindowKeepScreenOn(expected = true)
            }
        }

        try {
            step("Attach the surface without claiming the pre-existing window flag") {
                composeRule.setContent {
                    if (surfaceVisible.value) {
                        MaterialTheme {
                            PlayerVideoSurface(
                                content = contentState.value,
                                exoPlayer = { player },
                                playerViewFactory = { context ->
                                    player = player ?: ExoPlayer.Builder(context).build()
                                    PlayerView(context).also(playerViewReference::set)
                                },
                            )
                        }
                    }
                }
                composeRule.waitUntil { playerViewReference.get()?.isAttachedToWindow == true }
                assertPreExistingWindowFlagPreserved(playerViewReference, viewExpected = false)
            }

            step("Toggle view ownership through READY and BUFFERING") {
                publish(contentState, playbackState = Player.STATE_READY, playWhenReady = true)
                assertPreExistingWindowFlagPreserved(playerViewReference, viewExpected = true)

                publish(contentState, playbackState = Player.STATE_BUFFERING, playWhenReady = true)
                assertPreExistingWindowFlagPreserved(playerViewReference, viewExpected = true)
            }

            step("Release only view ownership for pause, suppression, and ENDED") {
                publish(contentState, playbackState = Player.STATE_READY, playWhenReady = false)
                assertPreExistingWindowFlagPreserved(playerViewReference, viewExpected = false)

                publish(
                    contentState = contentState,
                    playbackState = Player.STATE_READY,
                    playWhenReady = true,
                    suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                )
                assertPreExistingWindowFlagPreserved(playerViewReference, viewExpected = false)

                publish(contentState, playbackState = Player.STATE_ENDED, playWhenReady = true)
                assertPreExistingWindowFlagPreserved(playerViewReference, viewExpected = false)
            }

            step("Detach the surface while preserving external window ownership") {
                composeRule.runOnIdle {
                    surfaceVisible.value = false
                }
                composeRule.waitUntil {
                    playerViewReference.get().player == null &&
                        !playerViewReference.get().keepScreenOn
                }
                composeRule.runOnIdle {
                    assertFalse(playerViewReference.get().keepScreenOn)
                    assertNull(playerViewReference.get().player)
                    assertWindowKeepScreenOn(expected = true)
                }
            }
        } finally {
            try {
                composeRule.runOnIdle {
                    surfaceVisible.value = false
                }
                composeRule.waitForIdle()
            } finally {
                composeRule.runOnIdle {
                    player?.release()
                    player = null
                    composeRule.activity.window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    )
                    assertWindowKeepScreenOn(expected = false)
                }
            }
        }
    }

    private fun publish(
        contentState: androidx.compose.runtime.MutableState<PlayerContentState>,
        playbackState: Int,
        playWhenReady: Boolean,
        suppressionReason: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
    ) {
        composeRule.runOnIdle {
            contentState.value = contentState.value.withPlaybackSnapshot(
                PlaybackStatePolicy.derive(
                    isPlaying = playbackState == Player.STATE_READY && playWhenReady,
                    playWhenReady = playWhenReady,
                    playbackState = playbackState,
                    suppressionReason = suppressionReason,
                ),
            )
        }
    }

    private fun assertAttachedKeepScreenOn(
        playerViewReference: AtomicReference<PlayerView>,
        expected: Boolean,
    ) {
        composeRule.waitUntil {
            val viewKeepScreenOn = playerViewReference.get().keepScreenOn
            val windowKeepScreenOn = composeRule.activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            viewKeepScreenOn == expected && windowKeepScreenOn == expected
        }
        composeRule.runOnIdle {
            if (expected) {
                assertTrue(playerViewReference.get().keepScreenOn)
            } else {
                assertFalse(playerViewReference.get().keepScreenOn)
            }
            assertWindowKeepScreenOn(expected)
        }
    }

    private fun assertPreExistingWindowFlagPreserved(
        playerViewReference: AtomicReference<PlayerView>,
        viewExpected: Boolean,
    ) {
        composeRule.waitUntil {
            val viewKeepScreenOn = playerViewReference.get().keepScreenOn
            val windowKeepScreenOn = composeRule.activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            viewKeepScreenOn == viewExpected && windowKeepScreenOn
        }
        composeRule.runOnIdle {
            if (viewExpected) {
                assertTrue(playerViewReference.get().keepScreenOn)
            } else {
                assertFalse(playerViewReference.get().keepScreenOn)
            }
            assertWindowKeepScreenOn(expected = true)
        }
    }

    private fun assertWindowKeepScreenOn(expected: Boolean) {
        val isSet = composeRule.activity.window.attributes.flags and
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (expected) {
            assertTrue(isSet)
        } else {
            assertFalse(isSet)
        }
    }

    private fun content() = PlayerContentState(
        title = "Test",
        subtitle = null,
        isPlaying = false,
        playbackIntent = PlaybackIntent.Inactive,
        shouldKeepScreenOn = false,
        currentPosition = 0L,
        duration = 1L,
        bufferedPosition = 0L,
        controlsVisible = false,
        controlsFocusTarget = null,
        activePanel = ActivePanel.None,
        seekIndicator = null,
        playPauseIndicator = null,
        audioTracks = emptyList(),
        selectedAudioTrackIndex = 0,
        subtitleTracks = emptyList(),
        selectedSubtitleIndex = 0,
        soundModes = emptyList(),
        selectedSoundModeIndex = 0,
        subtitleSize = SubtitleSize.MEDIUM,
        qualities = emptyList(),
        selectedQualityIndex = 0,
        speeds = emptyList(),
        selectedSpeedIndex = 0,
        aspectRatios = emptyList(),
        selectedAspectRatioIndex = 0,
        bufferPresets = emptyList(),
        selectedBufferPresetIndex = 0,
        isMovie = true,
        hasNextEpisode = false,
        hasPreviousEpisode = false,
        canMarkCurrentWatched = false,
        isCurrentMediaWatched = false,
        isMarkCurrentWatchedInFlight = false,
        nextEpisodeCountdown = null,
        resumeDialog = null,
        episodes = null,
        currentEpisodeId = null,
    )
}
