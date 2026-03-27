package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.vm.ContentListVM

@Composable
internal fun ContentListScreenContent(
    contentListVm: ContentListVM,
    sections: List<SectionConfig>,
) {
    val state by contentListVm.collectViewState()
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    var focusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(mainContentFocus),
    ) {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3F),
            state = state.selectedItem,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2F),
        ) {
            itemsIndexed(sections) { index, config ->
                val isLastSection = index == sections.lastIndex
                SectionRow(
                    config = config,
                    isTargetRow = index == focusedSectionIndex,
                    onItemClick = { item ->
                        contentListVm.onAction(CommonAction.ItemSelected(item))
                    },
                    onItemFocused = { item ->
                        contentListVm.onAction(CommonAction.ItemFocused(item))
                    },
                    onSectionFocused = {
                        focusedSectionIndex = index
                    },
                    onShowAll = if (isLastSection) {
                        { contentListVm.onAction(ContentListAction.ShowAll(config)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
