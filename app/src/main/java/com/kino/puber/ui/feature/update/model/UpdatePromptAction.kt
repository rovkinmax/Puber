package com.kino.puber.ui.feature.update.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface UpdatePromptAction : UIAction {
    data object UpdateClicked : UpdatePromptAction
    data object DismissClicked : UpdatePromptAction
    data object OpenInstallPermissionSettingsClicked : UpdatePromptAction
    data object RetryInstallClicked : UpdatePromptAction
    data object OnResume : UpdatePromptAction
}
