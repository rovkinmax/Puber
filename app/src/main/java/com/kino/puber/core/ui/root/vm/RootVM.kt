package com.kino.puber.core.ui.root.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter

internal class RootVM(router: AppRouter) : PuberVM<Any>(router) {
    override val initialViewState: Any = Unit
}