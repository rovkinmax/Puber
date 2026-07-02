package com.kino.puber.ui.feature.main.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed class MainAction : UIAction {
    data class RefreshTab(val tab: MainTab) : MainAction()
}
