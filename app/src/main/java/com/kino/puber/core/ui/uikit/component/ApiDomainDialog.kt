@file:Suppress("MagicNumber")

package com.kino.puber.core.ui.uikit.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import kotlinx.coroutines.delay

private const val FOCUS_DELAY_MS = 100L

private val DialogWidth = 900.dp
private val DialogPadding = 20.dp
private val DialogCornerRadius = 18.dp
private val ContentSpacing = 20.dp
private val ActionButtonHeight = 48.dp
private val ActionButtonCornerRadius = 24.dp
private val FieldCornerRadius = 12.dp
private val QrSize = 160.dp

@Composable
internal fun ApiDomainDialog(
    state: ApiDomainDialogState?,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDetect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return

    var input by rememberSaveable(state.currentDomain, state.customDomain) {
        mutableStateOf(state.initialInput)
    }
    val inputFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state) {
        delay(FOCUS_DELAY_MS)
        saveFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Card(
            modifier = Modifier
                .then(modifier)
                .width(DialogWidth)
                .padding(24.dp),
            shape = RoundedCornerShape(DialogCornerRadius),
        ) {
            Row(
                modifier = Modifier.padding(DialogPadding),
                horizontalArrangement = Arrangement.spacedBy(ContentSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DomainForm(
                    currentDomain = state.currentDomain,
                    input = input,
                    inputFocusRequester = inputFocusRequester,
                    onInputChange = { input = it },
                    onSave = { onSave(input) },
                    onReset = onReset,
                    onDetect = onDetect,
                    onDismiss = onDismiss,
                    isDetecting = state.isDetecting,
                    saveFocusRequester = saveFocusRequester,
                    modifier = Modifier.weight(1f),
                )
                MirrorQr()
            }
        }
    }
}

@Composable
private fun DomainForm(
    currentDomain: String,
    input: String,
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDetect: () -> Unit,
    onDismiss: () -> Unit,
    isDetecting: Boolean,
    saveFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.api_domain_dialog_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.api_domain_dialog_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.api_domain_current, currentDomain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        DomainInput(
            input = input,
            inputFocusRequester = inputFocusRequester,
            onInputChange = onInputChange,
            onSave = onSave,
        )
        Text(
            text = stringResource(R.string.api_domain_dialog_input_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DialogActions(
            onSave = onSave,
            onReset = onReset,
            onDetect = onDetect,
            onDismiss = onDismiss,
            isDetecting = isDetecting,
            saveFocusRequester = saveFocusRequester,
        )
    }
}

@Composable
private fun DomainInput(
    input: String,
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(FieldCornerRadius),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(inputFocusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            decorationBox = { innerTextField ->
                if (input.isEmpty()) {
                    Text(
                        text = stringResource(R.string.api_domain_input_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun DialogActions(
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDetect: () -> Unit,
    onDismiss: () -> Unit,
    isDetecting: Boolean,
    saveFocusRequester: FocusRequester,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogActionButton(
            text = stringResource(R.string.api_domain_save),
            onClick = onSave,
            enabled = !isDetecting,
            primary = true,
            modifier = Modifier
                .focusRequester(saveFocusRequester),
        )
        DialogActionButton(
            text = stringResource(R.string.api_domain_reset),
            onClick = onReset,
            enabled = !isDetecting,
        )
        DialogActionButton(
            text = stringResource(
                if (isDetecting) {
                    R.string.api_domain_detecting
                } else {
                    R.string.api_domain_detect
                }
            ),
            onClick = onDetect,
            enabled = !isDetecting,
        )
        DialogActionButton(
            text = stringResource(R.string.api_domain_close),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isSelectPressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ActionButtonCornerRadius)
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
            .height(ActionButtonHeight)
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
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(enabled)
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
    fun handleEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
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

@Composable
private fun MirrorQr() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.qr_mirror_site),
            contentDescription = stringResource(R.string.api_domain_qr_content_description),
            modifier = Modifier.size(QrSize),
        )
        Text(
            text = stringResource(R.string.api_domain_qr_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.api_domain_mirror_site),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
