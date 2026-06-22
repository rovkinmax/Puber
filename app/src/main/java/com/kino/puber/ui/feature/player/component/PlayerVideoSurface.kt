package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.AspectRatioMode
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState

@Composable
internal fun PlayerVideoSurface(
    content: PlayerContentState,
    exoPlayer: () -> ExoPlayer?,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                player = exoPlayer()
            }
        },
        update = { view ->
            val currentPlayer = exoPlayer()
            if (view.player != currentPlayer) {
                view.player = currentPlayer
            }
            currentPlayer?.let { view.resizeMode = content.resizeMode() }
        },
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handlePlayerKeyEvent(
                    keyEvent = keyEvent.nativeKeyEvent,
                    hasResumeDialog = content.resumeDialog != null,
                    onAction = onAction,
                )
            },
    )
}

private fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    hasResumeDialog: Boolean,
    onAction: (UIAction) -> Unit,
): Boolean {
    if (keyEvent.action != KeyEvent.ACTION_DOWN || hasResumeDialog) return false
    val action = when (keyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerAction.SeekBackward
        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerAction.SeekForward
        KeyEvent.KEYCODE_DPAD_UP -> PlayerAction.ShowControls(FocusTarget.SeekBar)
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerAction.ShowControls(FocusTarget.Buttons)
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerAction.TogglePlayPause
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> PlayerAction.SeekForward
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> PlayerAction.SeekBackward
        else -> null
    }
    action?.let(onAction)
    return action != null
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun PlayerContentState.resizeMode(): Int {
    return when (aspectRatios.getOrNull(selectedAspectRatioIndex)?.mode) {
        AspectRatioMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
}
