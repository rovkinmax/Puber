package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.SnackbarMessage
import com.kino.puber.core.ui.uikit.model.UIAction

private val TvOverscanTop = 27.dp
private val TvOverscanHorizontal = 48.dp

@Composable
fun ScaffoldMessage(
    message: SnackbarMessage?,
    topPadding: Dp = TvOverscanTop,
    onAction: (UIAction) -> Unit,
) {
    if (message == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = TvOverscanHorizontal, end = TvOverscanHorizontal)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.TopCenter,
    ) {
        val snackState = remember { SnackbarHostState() }
        SnackbarHost(hostState = snackState) { data ->
            Snackbar(snackbarData = data)
        }

        LaunchedEffect(key1 = message.message) {
            val result = snackState.showSnackbar(
                message = message.message,
                duration = message.duration,
                actionLabel = message.actionLabel.ifEmpty { null },
            )
            when (result) {
                SnackbarResult.Dismissed -> onAction(CommonAction.SnackBarDismissed)
                SnackbarResult.ActionPerformed -> onAction(CommonAction.SnackBarActionPerformed)
            }
        }
    }
}