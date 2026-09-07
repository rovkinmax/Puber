package com.kino.puber.ui.feature.bookmarkpicker.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.OverlayPuberScreen
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerParams
import com.kino.puber.ui.feature.bookmarkpicker.vm.BookmarkPickerVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal data class BookmarkPickerScreen(
    private val params: BookmarkPickerParams,
) : OverlayPuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = "BookmarkPickerScreen_${params.itemId}_${params.resultCode}"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            viewModelOf(::BookmarkPickerVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<BookmarkPickerVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }

        BookmarkPickerScreenContent(state = state, onAction = onAction)

        val message by vm.collectMessage()
        ScaffoldMessage(message = message, onAction = onAction)
    }
}
