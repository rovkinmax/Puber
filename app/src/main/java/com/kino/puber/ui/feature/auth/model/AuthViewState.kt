package com.kino.puber.ui.feature.auth.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState

@Immutable
internal sealed class AuthViewState {
    @Immutable
    data class Loading(
        val showMirrorHint: Boolean = false,
        val statusMessage: String? = null,
        val apiDomainDialog: ApiDomainDialogState? = null,
    ) : AuthViewState()

    @Immutable
    data class Content(
        val code: String,
        val url: String,
        val timeLeft: String = "",
        val apiDomainDialog: ApiDomainDialogState? = null,
    ) : AuthViewState()
}
