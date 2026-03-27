package com.kino.puber.ui.feature.contentlist.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed class ContentListAction : UIAction {
    data class ShowAll(val config: SectionConfig) : ContentListAction()
}
