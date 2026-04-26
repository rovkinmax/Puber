package com.kino.puber.ui.feature.bookmarks.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.domain.interactor.bookmarks.BookmarkInteractor
import com.kino.puber.ui.feature.bookmarks.vm.BookmarksVM
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class BookmarksScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::BookmarkInteractor)
            scoped { VideoItemUIMapper(get()) }
            viewModelOf(::BookmarksVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<BookmarksVM>()
        val state by vm.collectViewState()
        val onAction = remember(vm) { vm::onAction }
        BookmarksScreenContent(
            state = state,
            onAction = onAction,
            onFolderSelected = vm::onFolderSelected,
        )
    }
}
