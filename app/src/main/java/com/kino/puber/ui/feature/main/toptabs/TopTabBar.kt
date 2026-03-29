package com.kino.puber.ui.feature.main.toptabs

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.kino.puber.ui.feature.main.model.MainTab

@Composable
internal fun TopTabBar(
    tabs: List<MainTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            key(tab.type) {
                Tab(
                    selected = index == selectedIndex,
                    onFocus = { onTabSelected(index) },
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = tab.label)
                }
            }
        }
    }
}
