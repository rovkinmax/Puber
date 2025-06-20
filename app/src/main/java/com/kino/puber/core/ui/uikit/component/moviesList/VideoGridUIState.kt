package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.modifier.createInitialFocusRestorerModifiers
import com.kino.puber.core.ui.uikit.component.modifier.ifElse

@Immutable
data class VideoGridUIState(
    val list: List<VideoGridItemUIState>,
)

sealed class VideoGridItemUIState {
    class Title(val title: String) : VideoGridItemUIState()
    class Items(val items: List<VideoItemUIState>) : VideoGridItemUIState()
}

@Composable
fun VideoGrid(
    modifier: Modifier = Modifier,
    state: VideoGridUIState,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.list) { indexC, columnItem ->
            when (columnItem) {

                is VideoGridItemUIState.Title -> Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    text = columnItem.title,
                    style = MaterialTheme.typography.titleLarge,
                )

                is VideoGridItemUIState.Items -> VideoGridItems(
                    columnItem = columnItem,
                    onItemClick = onItemClick,
                )
            }
        }
    }

}

@Composable
private fun VideoGridItems(
    columnItem: VideoGridItemUIState.Items,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    val focusRestorerModifier = createInitialFocusRestorerModifiers()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRestorerModifier.parentModifier),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        itemsIndexed(columnItem.items) { indexR, item ->
            VideoItem(
                modifier = Modifier.ifElse(
                    indexR == 0,
                    ifTrueModifier = focusRestorerModifier.childModifier,
                ),
                state = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}