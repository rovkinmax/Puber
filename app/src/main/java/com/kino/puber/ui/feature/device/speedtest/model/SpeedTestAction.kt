package com.kino.puber.ui.feature.device.speedtest.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface SpeedTestAction : UIAction {
    data object Start : SpeedTestAction
    data object Stop : SpeedTestAction
}
