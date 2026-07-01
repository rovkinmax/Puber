package com.kino.puber.ui.feature.auth.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed class AuthAction : UIAction {
    data object OpenApiDomainDialog : AuthAction()
    data object CloseApiDomainDialog : AuthAction()
    data class SaveApiDomain(val domain: String) : AuthAction()
    data object DetectApiDomain : AuthAction()
    data object ResetApiDomain : AuthAction()
}
