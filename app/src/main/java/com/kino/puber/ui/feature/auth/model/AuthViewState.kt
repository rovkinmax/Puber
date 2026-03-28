package com.kino.puber.ui.feature.auth.model

import androidx.compose.runtime.Immutable

@Immutable
internal sealed class AuthViewState {
    data object Loading : AuthViewState()

    @Immutable
    data class Content(val code: String, val url: String, val timeLeft: String = "") : AuthViewState()
}