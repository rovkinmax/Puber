package com.kino.puber.core.ui.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

data object PuberTheme {
    data object Defaults {
        val VideoItemWidth = 120.dp
        val VideoItemHeight = 180.dp
        const val DetailsWeight = 1F
        const val ContentWeight = 1F
    }
}

@Composable
fun PuberTheme(
    content: @Composable () -> Unit,
) {
    val colorSchemeTv = darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        error = Error60,
        errorContainer = Error60,
    )

    val colorScheme = androidx.compose.material3.darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        error = Error60,
        errorContainer = Error60,
    )
    MaterialTheme(
        colorScheme = colorSchemeTv,
        typography = Typography,
        content = {
            androidx.compose.material3.MaterialTheme(
                colorScheme = colorScheme,
                content = content
            )
        }
    )
}