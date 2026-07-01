package com.kino.puber.ui.feature.home.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed class HomeAction : UIAction {
    data object OpenApiDomainDialog : HomeAction()
    data object CloseApiDomainDialog : HomeAction()
    data class SaveApiDomain(val domain: String) : HomeAction()
    data object DetectApiDomain : HomeAction()
    data object ResetApiDomain : HomeAction()
}
