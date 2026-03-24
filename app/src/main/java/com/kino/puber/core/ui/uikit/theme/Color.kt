@file:Suppress("MagicNumber")

package com.kino.puber.core.ui.uikit.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val Error60 = Color(red = 228, green = 105, blue = 98)

val ColorScheme.focusHighlight: Color
    get() = Color(primary.value).copy(alpha = 0.15f)

fun Modifier.highlightOnFocus(isFocused: Boolean): Modifier = composed {
    if (isFocused) {
        this
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.focusHighlight, RoundedCornerShape(8.dp))
    } else {
        this
    }
}