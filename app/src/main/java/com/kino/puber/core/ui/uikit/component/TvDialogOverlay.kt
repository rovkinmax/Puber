package com.kino.puber.core.ui.uikit.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode

private const val DialogScrimAlpha = 0.62f

internal data class TvDialogFocusRestorer(
    val onDialogOpening: () -> Unit,
    val onDialogClosed: () -> Unit,
)

internal val LocalTvDialogFocusRestorer = compositionLocalOf<TvDialogFocusRestorer?> { null }

@Composable
internal fun TvDialogOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val focusRestorer = LocalTvDialogFocusRestorer.current
    val latestFocusRestorer = rememberUpdatedState(focusRestorer)
    val dismiss: () -> Unit = {
        onDismiss()
    }
    DisposableEffect(Unit) {
        onDispose {
            latestFocusRestorer.value?.onDialogClosed()
        }
    }
    if (!LocalInspectionMode.current) {
        BackHandler(onBack = dismiss)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = DialogScrimAlpha))
            .focusGroup(),
        contentAlignment = contentAlignment,
    ) {
        content(dismiss)
    }
}
