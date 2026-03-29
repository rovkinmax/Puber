package com.kino.puber.ui.feature.main.toptabs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.GearSix
import com.adamglin.phosphoricons.duotone.MagnifyingGlass
import com.kino.puber.ui.feature.main.model.MainTab

@Composable
internal fun TopTabBar(
    tabs: List<MainTab>,
    selectedIndex: Int,
    onTabFocused: (Int) -> Unit,
    onTabClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabRowModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(
            icon = PhosphorIcons.Duotone.MagnifyingGlass,
            contentDescription = "Search",
            onClick = onSearchClick,
        )

        Spacer(Modifier.width(16.dp))

        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = tabRowModifier.weight(1f),
        ) {
            tabs.forEachIndexed { index, tab ->
                key(tab.type) {
                    Tab(
                        selected = index == selectedIndex,
                        onFocus = { onTabFocused(index) },
                        onClick = onTabClick,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = tab.label)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        ActionIcon(
            icon = PhosphorIcons.Duotone.GearSix,
            contentDescription = "Settings",
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.Center),
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}