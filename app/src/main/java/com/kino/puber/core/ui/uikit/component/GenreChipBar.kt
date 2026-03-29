package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.data.api.models.Genre

@Composable
fun GenreChipBar(
    genres: List<Genre>,
    selectedGenreId: Int?,
    onGenreSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(16.dp)

    LazyRow(
        modifier = modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "all") {
            GenreChip(
                label = "Все",
                isSelected = selectedGenreId == null,
                onClick = { onGenreSelected(null) },
                shape = chipShape,
            )
        }
        items(items = genres, key = { it.id }) { genre ->
            GenreChip(
                label = genre.title,
                isSelected = selectedGenreId == genre.id,
                onClick = { onGenreSelected(genre.id) },
                shape = chipShape,
            )
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
