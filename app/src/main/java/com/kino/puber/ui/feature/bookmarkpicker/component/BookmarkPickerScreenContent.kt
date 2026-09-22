package com.kino.puber.ui.feature.bookmarkpicker.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvDialogOverlay
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.bookmarkpicker.component.preview.BookmarkPickerPreviewProvider
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkFolderUi
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerAction
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerViewState
import kotlinx.coroutines.delay

internal const val BOOKMARK_PICKER_DIALOG_TAG = "bookmark-picker-dialog"
internal const val BOOKMARK_CREATE_FOLDER_DIALOG_TAG = "bookmark-create-folder-dialog"
internal const val BOOKMARK_ADD_FOLDER_ROW_TAG = "bookmark-add-folder-row"
internal const val BOOKMARK_CREATE_FOLDER_CONFIRM_TAG = "bookmark-create-folder-confirm"
internal fun bookmarkFolderRowTag(folderId: Int) = "bookmark-folder-$folderId"

private const val FocusDelayMs = 100L
private val PickerDialogWidth = 560.dp
private val CreateDialogWidth = 620.dp
private val DialogPadding = 20.dp
private val DialogCornerRadius = 18.dp
private val DialogContentSpacing = 12.dp
private val FolderListMaxHeight = 480.dp
private val TextFieldCornerRadius = 12.dp

@Composable
internal fun BookmarkPickerScreenContent(
    state: BookmarkPickerViewState,
    onAction: (UIAction) -> Unit,
) {
    TvDialogOverlay(onDismiss = { onAction(BookmarkPickerAction.Dismiss) }) {
        when (state) {
            BookmarkPickerViewState.Loading -> LoadingDialog()
            is BookmarkPickerViewState.Error -> ErrorDialog(
                message = state.message,
                onRetry = { onAction(BookmarkPickerAction.Retry) },
                onDismiss = { onAction(BookmarkPickerAction.Dismiss) },
            )
            is BookmarkPickerViewState.Content -> if (state.isCreateFolderDialogVisible) {
                CreateFolderDialog(state = state, onAction = onAction)
            } else {
                FolderPickerDialog(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun LoadingDialog() {
    BookmarkDialogCard(
        width = PickerDialogWidth,
        testTag = BOOKMARK_PICKER_DIALOG_TAG,
    ) {
        DialogTitle(text = stringResource(R.string.bookmark_picker_title))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun FolderPickerDialog(
    state: BookmarkPickerViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.folders.firstOrNull()?.id) {
        delay(FocusDelayMs)
        runCatching { initialFocusRequester.requestFocus() }
    }

    BookmarkDialogCard(
        width = PickerDialogWidth,
        testTag = BOOKMARK_PICKER_DIALOG_TAG,
    ) {
        DialogTitle(text = stringResource(R.string.bookmark_picker_title))
        if (state.folders.isEmpty()) {
            Text(
                text = stringResource(R.string.bookmark_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FolderList(
                folders = state.folders,
                changingFolderIds = state.changingFolderIds,
                initialFocusRequester = initialFocusRequester,
                onFolderClick = { folderId ->
                    onAction(BookmarkPickerAction.FolderToggled(folderId))
                },
            )
        }
        AddFolderRow(
            modifier = if (state.folders.isEmpty()) {
                Modifier.focusRequester(initialFocusRequester)
            } else {
                Modifier
            },
            onClick = { onAction(BookmarkPickerAction.AddFolderRequested) },
        )
    }
}

@Composable
private fun FolderList(
    folders: List<BookmarkFolderUi>,
    changingFolderIds: Set<Int>,
    initialFocusRequester: FocusRequester,
    onFolderClick: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = FolderListMaxHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items = folders, key = BookmarkFolderUi::id) { folder ->
            FolderRow(
                folder = folder,
                isChanging = folder.id in changingFolderIds,
                modifier = if (folder.id == folders.first().id) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                },
                onClick = { onFolderClick(folder.id) },
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: BookmarkFolderUi,
    isChanging: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isChanging,
        modifier = modifier
            .fillMaxWidth()
            .testTag(bookmarkFolderRowTag(folder.id)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = folder.isSelected,
                onCheckedChange = null,
            )
            Text(
                text = folder.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (isChanging) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun AddFolderRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(BOOKMARK_ADD_FOLDER_ROW_TAG),
    ) {
        Text(
            text = stringResource(R.string.bookmark_picker_add_folder),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CreateFolderDialog(
    state: BookmarkPickerViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    val inputFocusRequester = remember { FocusRequester() }
    val createFocusRequester = remember { FocusRequester() }
    var wasKeyboardOpen by rememberSaveable { mutableStateOf(false) }
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun requestCreateFocus() {
        runCatching { createFocusRequester.requestFocus() }
    }

    LaunchedEffect(Unit) {
        delay(FocusDelayMs)
        runCatching { inputFocusRequester.requestFocus() }
    }
    LaunchedEffect(isKeyboardOpen) {
        if (wasKeyboardOpen && !isKeyboardOpen) {
            requestCreateFocus()
        }
        wasKeyboardOpen = isKeyboardOpen
    }

    BookmarkDialogCard(
        width = CreateDialogWidth,
        testTag = BOOKMARK_CREATE_FOLDER_DIALOG_TAG,
    ) {
        DialogTitle(text = stringResource(R.string.bookmark_picker_new_folder_title))
        NewFolderTextField(
            title = state.newFolderTitle,
            inputFocusRequester = inputFocusRequester,
            onTitleChanged = { title ->
                onAction(BookmarkPickerAction.NewFolderTitleChanged(title))
            },
            onKeyboardClosed = ::requestCreateFocus,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSafeButton(
                text = if (state.isCreatingFolder) {
                    stringResource(R.string.bookmark_picker_creating)
                } else {
                    stringResource(R.string.bookmark_picker_create)
                },
                enabled = state.canCreateFolder,
                primary = true,
                modifier = Modifier
                    .focusRequester(createFocusRequester)
                    .testTag(BOOKMARK_CREATE_FOLDER_CONFIRM_TAG),
                onClick = { onAction(BookmarkPickerAction.CreateFolder) },
            )
            TvSafeButton(
                text = stringResource(R.string.bookmark_picker_cancel),
                enabled = !state.isCreatingFolder,
                onClick = { onAction(BookmarkPickerAction.AddFolderDismissed) },
            )
        }
    }
}

@Composable
private fun NewFolderTextField(
    title: String,
    inputFocusRequester: FocusRequester,
    onTitleChanged: (String) -> Unit,
    onKeyboardClosed: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = title,
        onValueChange = onTitleChanged,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(TextFieldCornerRadius),
            )
            .focusRequester(inputFocusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && (event.key == Key.Back || event.key == Key.Escape)) {
                    keyboardController?.hide()
                    onKeyboardClosed()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (isFocused) {
                MaterialTheme.colorScheme.inverseOnSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                keyboardController?.hide()
                onKeyboardClosed()
            },
        ),
        decorationBox = { innerTextField ->
            NewFolderTextFieldDecoration(
                title = title,
                isFocused = isFocused,
                innerTextField = innerTextField,
            )
        },
    )
}

@Composable
private fun NewFolderTextFieldDecoration(
    title: String,
    isFocused: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    Box {
        if (title.isBlank()) {
            Text(
                text = stringResource(R.string.bookmark_picker_new_folder_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        innerTextField()
    }
}

@Composable
private fun ErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(FocusDelayMs)
        runCatching { retryFocusRequester.requestFocus() }
    }
    BookmarkDialogCard(
        width = PickerDialogWidth,
        testTag = BOOKMARK_PICKER_DIALOG_TAG,
    ) {
        DialogTitle(text = stringResource(R.string.bookmark_picker_title))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSafeButton(
                text = stringResource(R.string.bookmark_picker_retry),
                primary = true,
                modifier = Modifier.focusRequester(retryFocusRequester),
                onClick = onRetry,
            )
            TvSafeButton(
                text = stringResource(R.string.bookmark_picker_close),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun BookmarkDialogCard(
    width: Dp,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .width(width)
            .testTag(testTag),
        shape = RoundedCornerShape(DialogCornerRadius),
    ) {
        Column(
            modifier = Modifier.padding(DialogPadding),
            verticalArrangement = Arrangement.spacedBy(DialogContentSpacing),
            content = content,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview(showBackground = true, device = Devices.TV_1080p)
@Composable
private fun BookmarkPickerScreenPreview(
    @PreviewParameter(BookmarkPickerPreviewProvider::class) state: BookmarkPickerViewState,
) = PuberTheme {
    BookmarkPickerScreenContent(state = state, onAction = {})
}
