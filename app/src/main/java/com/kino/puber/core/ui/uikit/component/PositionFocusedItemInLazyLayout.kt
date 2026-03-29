package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
fun PositionFocusedItemInLazyLayout(
    parentFraction: Float = 0.3f,
    childFraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    val bringIntoViewSpec = remember(parentFraction, childFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
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
        content = content,
    )
}
