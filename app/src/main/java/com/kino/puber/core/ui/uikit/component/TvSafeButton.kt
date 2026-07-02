package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val ButtonHeight = 48.dp
private val ButtonCornerRadius = 24.dp

@Composable
internal fun TvSafeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isSelectPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ButtonCornerRadius)
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> colorScheme.surfaceVariant.copy(alpha = 0.36f)
        isFocused -> colorScheme.primary
        primary -> colorScheme.primaryContainer
        else -> colorScheme.surface
    }
    val contentColor = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> colorScheme.onPrimary
        primary -> colorScheme.onPrimaryContainer
        else -> colorScheme.onSurface
    }
    val borderColor = when {
        primary -> null
        isFocused -> colorScheme.primary
        else -> colorScheme.outline
    }

    Box(
        modifier = modifier
            .height(ButtonHeight)
            .background(containerColor, shape)
            .then(
                if (borderColor == null) {
                    Modifier
                } else {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) {
                    isSelectPressed = false
                }
            }
            .onTvSelectClick(
                enabled = enabled,
                isPressed = { isSelectPressed },
                setPressed = { isSelectPressed = it },
                onClick = onClick,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

private fun Modifier.onTvSelectClick(
    enabled: Boolean,
    isPressed: () -> Boolean,
    setPressed: (Boolean) -> Unit,
    onClick: () -> Unit,
): Modifier {
    fun handleEvent(event: KeyEvent): Boolean {
        if (!enabled) {
            setPressed(false)
            return false
        }
        if (!event.key.isSelectKey()) {
            return false
        }

        return when (event.type) {
            KeyEventType.KeyDown -> {
                setPressed(true)
                true
            }
            KeyEventType.KeyUp -> {
                if (isPressed()) {
                    setPressed(false)
                    onClick()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    return onPreviewKeyEvent(::handleEvent).onKeyEvent(::handleEvent)
}

private fun Key.isSelectKey(): Boolean = this == Key.DirectionCenter || this == Key.Enter
