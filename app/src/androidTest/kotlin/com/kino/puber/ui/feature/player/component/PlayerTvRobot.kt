package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kakaocup.compose.node.element.KNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class PlayerTvRobot(
    private val rule: ComposeContentTestRule,
    private val canonicalBack: (() -> Unit)? = null,
) {
    private val screen = PlayerComposeScreen(rule)

    val focus = PlayerFocusContract(rule, screen)

    fun press(key: PlayerRemoteKey) {
        pressWithoutSettling(key)
        rule.waitForIdle()
    }

    fun pressBack() {
        canonicalBack?.invoke() ?: press(PlayerRemoteKey.Back)
        rule.waitForIdle()
    }

    fun pressWithoutSettling(key: PlayerRemoteKey) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, key.keyCode))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, key.keyCode))
    }

    fun advanceOneFrame() {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    fun advanceClockBy(milliseconds: Long) {
        rule.mainClock.advanceTimeBy(milliseconds)
        rule.waitForIdle()
    }

    fun focusButton(button: KNode, dpadRightSteps: Int) {
        repeat(dpadRightSteps) {
            press(PlayerRemoteKey.Right)
        }
        button.assertIsFocused()
    }

    fun waitForControlsToBeDisposed() {
        rule.waitUntil(timeoutMillis = FOCUS_TIMEOUT_MS) {
            rule.onAllNodes(
                hasTestTag(PlayerScreenTestTags.AudioSubtitles),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    fun waitUntilFocused(button: KNode) {
        rule.waitUntil(timeoutMillis = FOCUS_TIMEOUT_MS) {
            runCatching {
                button.assertIsFocused()
                true
            }.getOrDefault(false)
        }
        button.assertIsFocused()
    }

    fun assertControlsVisible(controlsVisible: Boolean) {
        assertTrue(controlsVisible)
        screen.audioSubtitlesButton.assertIsDisplayed()
    }

    fun screen(actions: PlayerComposeScreen.() -> Unit) {
        screen.actions()
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 5_000L
    }
}

internal enum class PlayerRemoteKey(internal val keyCode: Int) {
    Up(KeyEvent.KEYCODE_DPAD_UP),
    Down(KeyEvent.KEYCODE_DPAD_DOWN),
    Left(KeyEvent.KEYCODE_DPAD_LEFT),
    Right(KeyEvent.KEYCODE_DPAD_RIGHT),
    Select(KeyEvent.KEYCODE_DPAD_CENTER),
    Enter(KeyEvent.KEYCODE_ENTER),
    Back(KeyEvent.KEYCODE_BACK),
    PlayPause(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    Play(KeyEvent.KEYCODE_MEDIA_PLAY),
    Pause(KeyEvent.KEYCODE_MEDIA_PAUSE),
    Rewind(KeyEvent.KEYCODE_MEDIA_REWIND),
    FastForward(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
    SkipBackward(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD),
    SkipForward(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD),
}

internal class PlayerFocusContract(
    private val rule: ComposeContentTestRule,
    private val screen: PlayerComposeScreen,
) {
    fun assertExactlyOneFocusedNode() {
        val focusedNodes = focusedNodes()
        assertEquals(
            "PlayerScreen must have exactly one focused semantics node; " +
                "focusedBounds=${focusedNodes.map(SemanticsNode::boundsInRoot)}",
            1,
            focusedNodes.size,
        )
    }

    fun assertFocusedPlayerSurface() {
        screen.playerSurface.assertIsDisplayed()
        screen.playerSurface.assertIsFocused()
        val focusedNode = focusedNode()
        val rootBounds = rootBounds()
        assertTrue(
            "Focused player anchor must cover the root width: " +
                "focused=${focusedNode.boundsInRoot}, root=$rootBounds",
            focusedNode.boundsInRoot.width >= rootBounds.width * 0.9f,
        )
        assertTrue(
            "Focused player anchor must cover the root height: " +
                "focused=${focusedNode.boundsInRoot}, root=$rootBounds",
            focusedNode.boundsInRoot.height >= rootBounds.height * 0.9f,
        )
    }

    fun assertFocusedSeekBar() {
        screen.seekBar.assertIsDisplayed()
        screen.seekBar.assertIsFocused()
        val focusedNode = focusedNode()
        val rootWidth = rootBounds().width
        assertTrue(
            "Seek bar must be the focused wide control: " +
                "focused=${focusedNode.boundsInRoot}, rootWidth=$rootWidth",
            focusedNode.boundsInRoot.width >= rootWidth * 0.5f,
        )
        assertTrue(
            "Seek bar must not be the full-screen player anchor: " +
                "focused=${focusedNode.boundsInRoot}, rootWidth=$rootWidth",
            focusedNode.boundsInRoot.width < rootWidth * 0.9f,
        )
    }

    fun assertFocusedControl() {
        screen.playPauseButton.assertIsDisplayed()
        screen.playPauseButton.assertIsFocused()
        waitUntilFocusedTarget { node ->
            val rootBounds = rootBounds()
            node.boundsInRoot.width < rootBounds.width * 0.5f &&
                node.boundsInRoot.height < rootBounds.height * 0.9f
        }
        val focusedNode = focusedNode()
        val rootBounds = rootBounds()
        assertTrue(
            "Control focus must not remain on the full-screen player anchor: " +
                "focused=${focusedNode.boundsInRoot}, root=$rootBounds",
            focusedNode.boundsInRoot.width < rootBounds.width * 0.5f &&
                focusedNode.boundsInRoot.height < rootBounds.height * 0.9f,
        )
    }

    fun assertPlayerAnchorFocusable(expected: Boolean) {
        val rootBounds = rootBounds()
        val fullScreenFocusableCount = rule
            .onAllNodes(isFocusable(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .count { node ->
                node.boundsInRoot.width >= rootBounds.width * 0.9f &&
                    node.boundsInRoot.height >= rootBounds.height * 0.9f
            }
        assertEquals(
            "Only the typed Player owner may expose the full-screen focus anchor; root=$rootBounds",
            if (expected) 1 else 0,
            fullScreenFocusableCount,
        )
    }

    fun assertHiddenControlsAreNotFocused() {
        screen.audioSubtitlesButton.assertDoesNotExist()
        screen.videoSettingsButton.assertDoesNotExist()
    }

    fun assertHiddenPlayerIsNotFocused() {
        val focusedNode = focusedNode()
        val rootBounds = rootBounds()
        assertFalse(
            "Player anchor must lose focus after controls reopen: " +
                "focused=${focusedNode.boundsInRoot}, root=$rootBounds",
            focusedNode.boundsInRoot.width >= rootBounds.width * 0.9f,
        )
    }

    private fun focusedNode(): SemanticsNode = focusedNodes().single()

    private fun focusedNodes(): List<SemanticsNode> = rule
        .onAllNodes(isFocused(), useUnmergedTree = true)
        .fetchSemanticsNodes()

    private fun rootBounds() = rule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun waitUntilFocusedTarget(predicate: (SemanticsNode) -> Boolean) {
        rule.waitUntil(timeoutMillis = FOCUS_TIMEOUT_MS) {
            focusedNodes().singleOrNull()?.let(predicate) == true
        }
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 5_000L
    }
}
