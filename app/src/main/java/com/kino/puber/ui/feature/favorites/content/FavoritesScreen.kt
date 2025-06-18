package com.kino.puber.ui.feature.favorites.content

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.modifier.ifElse
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.parcelize.Parcelize

@Parcelize
internal class FavoritesScreen(private val tab: TabType) : PuberScreen {
    override val key: ScreenKey
        get() = tab.name

    @Composable
    override fun Content() {
        val fallbackFocusRequester = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer(fallbackFocusRequester)
                .focusGroup(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Favorites TBD $tab")
            LazyRow(contentPadding = PaddingValues(start = 16.dp)) {
                items(count = 20) { index ->
                    Card(
                        modifier = Modifier.ifElse(
                            index == 0,
                            Modifier.focusRequester(fallbackFocusRequester),
                        ),
                        onClick = {},
                    ) {
                        Icon(
                            modifier = Modifier.size(94.dp),
                            imageVector = Icons.Default.Accessibility,
                            contentDescription = null,
                        )

                        Text("Favorites TBD $tab")
                    }
                }
            }
        }
    }
}