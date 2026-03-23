package com.kino.puber.core.ui.uikit.model

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Immutable

@Immutable
sealed class SnackbarMessage(
    val message: String,
    val duration: SnackbarDuration,
    val actionLabel: String,
) {
    @Immutable
    class Short(message: String, actionLabel: String = "") :
        SnackbarMessage(message, SnackbarDuration.Short, actionLabel)

    @Immutable
    class Long(message: String, actionLabel: String = "") :
        SnackbarMessage(message, SnackbarDuration.Long, actionLabel)

    @Immutable
    class Infinite(message: String, actionLabel: String = "") :
        SnackbarMessage(message, SnackbarDuration.Indefinite, actionLabel)
}