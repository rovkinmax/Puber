package com.kino.puber.ui.feature.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.Broadcast
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmReel
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.GearSix
import com.adamglin.phosphoricons.duotone.Ghost
import com.adamglin.phosphoricons.duotone.Heart
import com.adamglin.phosphoricons.duotone.MicrophoneStage
import com.adamglin.phosphoricons.duotone.MonitorPlay
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.TelevisionSimple
import com.adamglin.phosphoricons.duotone.Trophy
import com.kino.puber.core.ui.uikit.theme.PuberTheme

private data class PreviewMenuItem(
    val icon: ImageVector,
    val label: String,
)

private val previewMenuItems = listOf(
    PreviewMenuItem(PhosphorIcons.Duotone.Heart, "Я смотрю"),
    PreviewMenuItem(PhosphorIcons.Duotone.BookmarkSimple, "Закладки"),
    PreviewMenuItem(PhosphorIcons.Duotone.ClockCounterClockwise, "История"),
    PreviewMenuItem(PhosphorIcons.Duotone.FilmSlate, "Фильмы"),
    PreviewMenuItem(PhosphorIcons.Duotone.TelevisionSimple, "Сериалы"),
    PreviewMenuItem(PhosphorIcons.Duotone.Ghost, "Мультфильмы"),
    PreviewMenuItem(PhosphorIcons.Duotone.MicrophoneStage, "Концерты"),
    PreviewMenuItem(PhosphorIcons.Duotone.FilmReel, "Док. Фильмы"),
    PreviewMenuItem(PhosphorIcons.Duotone.MonitorPlay, "Док. Сериалы"),
    PreviewMenuItem(PhosphorIcons.Duotone.Broadcast, "ТВ Шоу"),
    PreviewMenuItem(PhosphorIcons.Duotone.Playlist, "Подборки"),
    PreviewMenuItem(PhosphorIcons.Duotone.Trophy, "Спорт ТВ"),
    PreviewMenuItem(PhosphorIcons.Duotone.GearSix, "Настройки"),
)

@Preview(name = "Side Menu — Phosphor Duotone", device = TV_1080p)
@Composable
private fun SideMenuPreview() = PuberTheme {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        previewMenuItems.forEachIndexed { index, item ->
            val isSelected = index == 0
            Row(
                modifier = Modifier
                    .run {
                        if (isSelected) {
                            background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.shapes.small,
                            )
                        } else this
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
