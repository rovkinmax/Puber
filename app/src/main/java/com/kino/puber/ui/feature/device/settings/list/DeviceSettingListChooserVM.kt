package com.kino.puber.ui.feature.device.settings.list

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter

internal class DeviceSettingListChooserVM(
    router: AppRouter,
) : PuberVM<DeviceSettingListChooserViewState>(router) {

    override val initialViewState: DeviceSettingListChooserViewState
        get() = DeviceSettingListChooserViewState()

} 