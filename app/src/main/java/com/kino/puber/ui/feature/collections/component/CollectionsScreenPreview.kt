package com.kino.puber.ui.feature.collections.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.collections.model.CollectionUIState
import com.kino.puber.ui.feature.collections.model.CollectionsViewState

private val previewCollections = (1..9).map { i ->
    CollectionUIState(
        id = i,
        title = "Подборка $i — Лучшие фильмы",
        imageUrl = "",
        wideImageUrl = "",
        count = i * 5 + 10,
    )
}

@Preview(name = "Collections — Loading", device = TV_1080p)
@Composable
private fun CollectionsLoadingPreview() = PuberTheme {
    CollectionsScreenContent(
        state = CollectionsViewState.Loading,
        onCollectionClick = {},
        onLoadMore = {},
    )
}

@Preview(name = "Collections — Error", device = TV_1080p)
@Composable
private fun CollectionsErrorPreview() = PuberTheme {
    CollectionsScreenContent(
        state = CollectionsViewState.Error("Не удалось загрузить подборки"),
        onCollectionClick = {},
        onLoadMore = {},
    )
}

@Preview(name = "Collections — Content", device = TV_1080p)
@Composable
private fun CollectionsContentPreview() = PuberTheme {
    CollectionsScreenContent(
        state = CollectionsViewState.Content(collections = previewCollections),
        onCollectionClick = {},
        onLoadMore = {},
    )
}

@Preview(name = "CollectionCard", device = TV_1080p)
@Composable
private fun CollectionCardPreview() = PuberTheme {
    CollectionCard(
        state = CollectionUIState(
            id = 1,
            title = "Лучшие фильмы 2025 года",
            imageUrl = "",
            wideImageUrl = "",
            count = 42,
        ),
        onClick = {},
    )
}
