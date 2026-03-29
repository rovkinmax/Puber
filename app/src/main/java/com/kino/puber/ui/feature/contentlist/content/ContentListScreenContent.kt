package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.GenreChipBar
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.ContentListViewState
import com.kino.puber.ui.feature.contentlist.model.SectionConfig

@Composable
internal fun ContentListScreenContent(
    state: ContentListViewState,
    sections: List<SectionConfig>,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    var focusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(mainContentFocus),
    ) {
        if (state.showDetailPanel) {
            VideoItemGridDetails(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PuberTheme.Defaults.DetailsWeight),
                state = state.selectedItem,
            )
        }

        if (state.showGenreChips && state.genres.isNotEmpty()) {
            GenreChipBar(
                genres = state.genres,
                selectedGenreId = state.selectedGenreId,
                onGenreSelected = { genreId -> onAction(ContentListAction.GenreSelected(genreId)) },
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (state.showDetailPanel) PuberTheme.Defaults.ContentWeight else 1f),
            contentPadding = PaddingValues(bottom = PuberTheme.Defaults.HorizontalVideoItemHeight),
        ) {
            sections.forEachIndexed { index, config ->
                val isLastSection = index == sections.lastIndex

                item(key = "title_${config.id}", contentType = "section_title") {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        text = config.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                item(key = "content_${config.id}", contentType = "section_content") {
                    val rememberedOnItemClick = remember(config.id) {
                        { item: VideoItemUIState -> onAction(CommonAction.ItemSelected(item)) }
                    }
                    val rememberedOnItemFocused = remember(config.id) {
                        { item: VideoItemUIState -> onAction(CommonAction.ItemFocused(item)) }
                    }
                    val rememberedOnSectionFocused = remember(index) {
                        { focusedSectionIndex = index }
                    }
                    val rememberedOnShowAll = remember(config.id, isLastSection) {
                        if (isLastSection) {
                            { onAction(ContentListAction.ShowAll(config)) }
                        } else {
                            null
                        }
                    }
                    SectionRowContent(
                        config = config,
                        isTargetRow = index == focusedSectionIndex,
                        onItemClick = rememberedOnItemClick,
                        onItemFocused = rememberedOnItemFocused,
                        onSectionFocused = rememberedOnSectionFocused,
                        onShowAll = rememberedOnShowAll,
                    )
                }
            }
        }
    }
}
