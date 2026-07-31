package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.GenreChipBar
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.ContentListViewState
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.ui.feature.contentlist.vm.SectionVM
import com.kino.puber.core.di.LocalPuberKoinScope
import org.koin.core.qualifier.named

@Composable
internal fun ContentListScreenContent(
    state: ContentListViewState,
    sections: List<SectionConfig>,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    var focusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var contextMenuTarget by remember { mutableStateOf<ContentListContextMenuTarget?>(null) }

    val scope = LocalPuberKoinScope.current ?: return
    val sectionVms = remember {
        sections.map { config -> scope.get<SectionVM>(named(config.id)) }
    }
    val sectionStates = sectionVms.mapIndexed { index, vm ->
        key(sections[index].id) {
            val s by vm.collectState()
            s
        }
    }

    androidx.compose.foundation.layout.Box {
        ContentListLayout(
            state = state,
            sections = sections,
            sectionVms = sectionVms,
            sectionStates = sectionStates,
            focusedSectionIndex = focusedSectionIndex,
            onSectionFocused = { focusedSectionIndex = it },
            onContextMenu = { item, sectionVm ->
                contextMenuTarget = ContentListContextMenuTarget(item, sectionVm)
            },
            onAction = onAction,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(mainContentFocus),
        )

        val activeContextMenuTarget = contextMenuTarget
        VideoItemContextMenuDialog(
            item = activeContextMenuTarget?.item,
            onDismiss = { contextMenuTarget = null },
            onAction = { action ->
                if (action is CommonAction.ItemSavedChanged<*>) {
                    activeContextMenuTarget?.sectionVm?.onAction(action)
                } else {
                    onAction(action)
                }
            },
        )
    }
}

@Composable
private fun ContentListLayout(
    state: ContentListViewState,
    sections: List<SectionConfig>,
    sectionVms: List<SectionVM>,
    sectionStates: List<SectionState>,
    focusedSectionIndex: Int,
    onSectionFocused: (Int) -> Unit,
    onContextMenu: (VideoItemUIState, SectionVM) -> Unit,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
                .weight(if (state.showDetailPanel) PuberTheme.Defaults.ContentWeight else 1f)
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(bottom = PuberTheme.Defaults.HorizontalVideoItemHeight),
        ) {
            heroItem(state, onAction)
            sectionItems(
                sections = sections,
                sectionVms = sectionVms,
                sectionStates = sectionStates,
                focusedSectionIndex = focusedSectionIndex,
                onSectionFocused = onSectionFocused,
                onContextMenu = onContextMenu,
                onAction = onAction,
            )
        }
    }
}

private fun LazyListScope.heroItem(
    state: ContentListViewState,
    onAction: (UIAction) -> Unit,
) {
    if (state.isHeroLoading || state.heroItems.isNotEmpty()) {
        item(key = "hero", contentType = "hero") {
            if (state.heroItems.isEmpty()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
            } else {
                HeroCarousel(
                    items = state.heroItems,
                    onItemClick = { itemId ->
                        onAction(ContentListAction.HeroSelected(itemId))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun LazyListScope.sectionItems(
    sections: List<SectionConfig>,
    sectionVms: List<SectionVM>,
    sectionStates: List<SectionState>,
    focusedSectionIndex: Int,
    onSectionFocused: (Int) -> Unit,
    onContextMenu: (VideoItemUIState, SectionVM) -> Unit,
    onAction: (UIAction) -> Unit,
) {
    sections.forEachIndexed { index, config ->
        sectionItem(
            index = index,
            config = config,
            sectionVm = sectionVms[index],
            sectionState = sectionStates[index],
            isLastSection = index == sections.lastIndex,
            isTargetRow = index == focusedSectionIndex,
            onSectionFocused = onSectionFocused,
            onContextMenu = onContextMenu,
            onAction = onAction,
        )
    }
}

private fun LazyListScope.sectionItem(
    index: Int,
    config: SectionConfig,
    sectionVm: SectionVM,
    sectionState: SectionState,
    isLastSection: Boolean,
    isTargetRow: Boolean,
    onSectionFocused: (Int) -> Unit,
    onContextMenu: (VideoItemUIState, SectionVM) -> Unit,
    onAction: (UIAction) -> Unit,
) {
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
            { onSectionFocused(index) }
        }
        val rememberedOnShowAll = remember(config.id, isLastSection) {
            if (isLastSection) {
                { onAction(ContentListAction.ShowAll(config)) }
            } else {
                null
            }
        }
        SectionRowContent(
            state = sectionState,
            config = config,
            isTargetRow = isTargetRow,
            onItemClick = rememberedOnItemClick,
            onItemContextMenu = { onContextMenu(it, sectionVm) },
            onItemFocused = rememberedOnItemFocused,
            onSectionFocused = rememberedOnSectionFocused,
            onRetry = { sectionVm.onAction(CommonAction.RetryClicked) },
            onLoadMore = { sectionVm.onAction(CommonAction.LoadMore) },
            onShowAll = rememberedOnShowAll,
        )
    }
}

private data class ContentListContextMenuTarget(
    val item: VideoItemUIState,
    val sectionVm: SectionVM,
)
