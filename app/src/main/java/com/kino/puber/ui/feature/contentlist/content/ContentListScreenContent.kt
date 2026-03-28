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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
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
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(PuberTheme.Defaults.DetailsWeight),
            state = state.selectedItem,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(PuberTheme.Defaults.ContentWeight),
            contentPadding = PaddingValues(bottom = PuberTheme.Defaults.VideoItemHeight),
        ) {
            sections.forEachIndexed { index, config ->
                val isLastSection = index == sections.lastIndex

                item(key = "title_${config.id}") {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        text = config.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                item(key = "content_${config.id}") {
                    SectionRowContent(
                        config = config,
                        isTargetRow = index == focusedSectionIndex,
                        onItemClick = { item ->
                            onAction(CommonAction.ItemSelected(item))
                        },
                        onItemFocused = { item ->
                            onAction(CommonAction.ItemFocused(item))
                        },
                        onSectionFocused = {
                            focusedSectionIndex = index
                        },
                        onShowAll = if (isLastSection) {
                            { onAction(ContentListAction.ShowAll(config)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
