package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
internal fun SettingsPanelColumn(
    header: String,
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelMedium,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items, key = { index, _ -> index }) { index, item ->
                SettingsPanelItem(
                    text = item,
                    selected = index == selectedIndex,
                    focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                    colors = SettingsPanelItemColors(
                        primary = primaryColor,
                        onSurface = onSurfaceColor,
                    ),
                    onClick = { onItemSelected(index) },
                )
            }
        }
    }
}

private data class SettingsPanelItemColors(
    val primary: Color,
    val onSurface: Color,
)

@Composable
private fun SettingsPanelItem(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    colors: SettingsPanelItemColors,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = colors.primary.copy(alpha = 0.2f),
            contentColor = if (selected) colors.primary else colors.onSurface,
            focusedContentColor = if (selected) colors.primary else colors.onSurface,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionIconSpacer(selected = selected, primaryColor = colors.primary)
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SelectionIconSpacer(selected: Boolean, primaryColor: Color) {
    if (selected) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
    } else {
        Spacer(modifier = Modifier.width(28.dp))
    }
}
