package com.kino.puber.core.ui.uikit.component

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal val LocalRapidScrollActive = staticCompositionLocalOf<MutableState<Boolean>?> { null }

enum class DpadScrollAxis {
    Horizontal,
    Vertical,
}

@Composable
fun PositionFocusedItemInLazyLayout(
    parentFraction: Float = 0.3f,
    childFraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    val rapidScrollActive = remember { mutableStateOf(false) }
    val bringIntoViewSpec = remember(parentFraction, childFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                if (rapidScrollActive.value && offset >= 0 && offset + size <= containerSize) {
                    return 0f
                }
                val initialTargetForLeadingEdge =
                    parentFraction * containerSize - (childFraction * size)
                val spaceAvailableToShowItem = containerSize - initialTargetForLeadingEdge
                val targetForLeadingEdge =
                    if (size <= containerSize && spaceAvailableToShowItem < size) {
                        containerSize - size
                    } else {
                        initialTargetForLeadingEdge
                    }
                return offset - targetForLeadingEdge
            }
        }
    }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        LocalRapidScrollActive provides rapidScrollActive,
        content = content,
    )
}

@Composable
fun Modifier.dpadScrollOptimization(axis: DpadScrollAxis): Modifier {
    val rapidScrollActive = LocalRapidScrollActive.current ?: return this
    return this.onPreviewKeyEvent { event ->
        val keyCode = event.nativeKeyEvent.keyCode
        when (event.type) {
            KeyEventType.KeyDown -> {
                rapidScrollActive.value = event.nativeKeyEvent.repeatCount > 0 && axis.matches(keyCode)
                false
            }
            KeyEventType.KeyUp -> {
                rapidScrollActive.value = false
                false
            }
            else -> false
        }
    }
}

private fun DpadScrollAxis.matches(keyCode: Int): Boolean {
    return when (this) {
        DpadScrollAxis.Horizontal -> keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        DpadScrollAxis.Vertical -> keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
    }
}
