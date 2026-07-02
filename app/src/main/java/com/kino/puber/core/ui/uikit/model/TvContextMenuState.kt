package com.kino.puber.core.ui.uikit.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvContextMenuAction(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
)
