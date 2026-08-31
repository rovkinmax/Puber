package com.kino.puber.ui.feature.player.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kino.puber.ui.feature.player.model.AspectRatioMode
import com.kino.puber.ui.feature.player.model.PlayerContentState

@Composable
internal fun PlayerVideoSurface(
    content: PlayerContentState,
    exoPlayer: () -> ExoPlayer?,
    playerViewFactory: (Context) -> PlayerView = { context -> PlayerView(context) },
) {
    val windowKeepScreenOnBinding = remember { WindowKeepScreenOnBinding() }
    AndroidView(
        factory = { context ->
            playerViewFactory(context).apply {
                useController = false
                updateKeepScreenOn(
                    shouldKeepScreenOn = content.shouldKeepScreenOn,
                    window = context.findActivity()?.window,
                    windowBinding = windowKeepScreenOnBinding,
                )
                player = exoPlayer()
            }
        },
        update = { view ->
            view.updateKeepScreenOn(
                shouldKeepScreenOn = content.shouldKeepScreenOn,
                window = view.context.findActivity()?.window,
                windowBinding = windowKeepScreenOnBinding,
            )
            val currentPlayer = exoPlayer()
            if (view.player != currentPlayer) {
                view.player = currentPlayer
            }
            currentPlayer?.let { view.resizeMode = content.resizeMode() }
        },
        onRelease = { view ->
            view.keepScreenOn = false
            windowKeepScreenOnBinding.release(view)
            view.player = null
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun PlayerView.updateKeepScreenOn(
    shouldKeepScreenOn: Boolean,
    window: Window?,
    windowBinding: WindowKeepScreenOnBinding,
) {
    keepScreenOn = shouldKeepScreenOn
    windowBinding.update(this, window, shouldKeepScreenOn)
}

private class WindowKeepScreenOnBinding : View.OnAttachStateChangeListener {
    private var boundView: View? = null
    private var requestedWindow: Window? = null
    private var shouldKeepScreenOn = false
    private var ownedWindow: Window? = null

    fun update(view: View, window: Window?, shouldKeepScreenOn: Boolean) {
        if (boundView !== view) {
            boundView?.removeOnAttachStateChangeListener(this)
            releaseOwnedWindow()
            boundView = view
            view.addOnAttachStateChangeListener(this)
        }

        requestedWindow = window
        this.shouldKeepScreenOn = shouldKeepScreenOn
        synchronizeOwnership()
    }

    fun release(view: View) {
        if (boundView === view) {
            view.removeOnAttachStateChangeListener(this)
            boundView = null
            requestedWindow = null
            shouldKeepScreenOn = false
            releaseOwnedWindow()
        }
    }

    override fun onViewAttachedToWindow(view: View) {
        if (boundView === view) synchronizeOwnership()
    }

    override fun onViewDetachedFromWindow(view: View) {
        if (boundView === view) releaseOwnedWindow()
    }

    private fun synchronizeOwnership() {
        val view = boundView
        val window = requestedWindow
        if (!shouldKeepScreenOn || view?.isAttachedToWindow != true || window == null) {
            releaseOwnedWindow()
            return
        }
        if (
            ownedWindow === window &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        ) {
            return
        }

        releaseOwnedWindow()
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ownedWindow = window
        }
    }

    private fun releaseOwnedWindow() {
        ownedWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ownedWindow = null
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
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
