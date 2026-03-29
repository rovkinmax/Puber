package com.kino.puber.ui.feature.search.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItem
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.search.model.SearchViewState

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay

private const val GRID_COLUMNS = 5
private const val SHIMMER_ITEM_COUNT = 15
private const val SEARCH_TAG = "search_query"
private const val FOCUS_TARGET_TEXT_FIELD = 0
private const val FOCUS_TARGET_GRID = 1

@Composable
internal fun SearchScreenContent(
    state: SearchViewState,
    onAction: (UIAction) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val textFieldFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    var focusTarget by rememberSaveable { mutableStateOf(FOCUS_TARGET_TEXT_FIELD) }

    // Restore focus on (re-)composition: text field on first launch, grid on return from details
    LaunchedEffect(Unit) {
        delay(100)
        when {
            focusTarget == FOCUS_TARGET_GRID && state is SearchViewState.Content ->
                gridFocusRequester.requestFocus()
            else ->
                textFieldFocusRequester.requestFocus()
        }
    }

    // Move focus to grid when IME dismisses via Back
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val isKeyboardOpen = imeBottomPx > 0
    var wasKeyboardOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isKeyboardOpen) {
        if (wasKeyboardOpen && !isKeyboardOpen && state is SearchViewState.Content) {
            focusTarget = FOCUS_TARGET_GRID
            gridFocusRequester.requestFocus()
        }
        wasKeyboardOpen = isKeyboardOpen
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchInputField(
            query = query,
            textFieldFocusRequester = textFieldFocusRequester,
            gridFocusRequester = gridFocusRequester,
            hasResults = state is SearchViewState.Content,
            onQueryChanged = { text ->
                query = text
                onAction(CommonAction.TextChanged(text, SEARCH_TAG))
            },
        )
        SearchResultsArea(
            state = state,
            gridFocusRequester = gridFocusRequester,
            onItemClick = { item ->
                focusTarget = FOCUS_TARGET_GRID
                onAction(CommonAction.ItemSelected(item))
            },
        )
    }
}

@Composable
private fun SearchInputField(
    query: String,
    textFieldFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    hasResults: Boolean,
    onQueryChanged: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(textFieldFocusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    if (hasResults) gridFocusRequester.requestFocus()
                },
            ),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun SearchResultsArea(
    state: SearchViewState,
    gridFocusRequester: FocusRequester,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is SearchViewState.Idle -> CenteredText(stringResource(R.string.search_hint))
            is SearchViewState.Loading -> ShimmerSearchGrid()
            is SearchViewState.Empty -> CenteredText(stringResource(R.string.search_no_results))
            is SearchViewState.Error -> CenteredText(state.message)
            is SearchViewState.Content -> SearchResultsGrid(
                state = state,
                gridFocusRequester = gridFocusRequester,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun SearchResultsGrid(
    state: SearchViewState.Content,
    gridFocusRequester: FocusRequester,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(gridFocusRequester)
            .focusRestorer(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
            VideoItem(
                state = item.copy(showTitle = true),
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun ShimmerSearchGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items(SHIMMER_ITEM_COUNT, contentType = { "shimmer" }) {
            Box(
                modifier = Modifier
                    .size(
                        width = PuberTheme.Defaults.VideoItemWidth,
                        height = PuberTheme.Defaults.VideoItemHeight,
                    )
                    .placeholder(visible = true)
                    .focusable(),
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
