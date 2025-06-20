package com.kino.puber.ui.feature.favorites.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.favorites.FavoritesInteractor
import com.kino.puber.ui.feature.favorites.model.FavoriteItemUIMapper
import com.kino.puber.ui.feature.favorites.vm.FavoriteVM
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class FavoritesScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::FavoriteItemUIMapper)
            scopedOf(::FavoritesInteractor)
            viewModelOf(::FavoriteVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<FavoriteVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        FavoriteScreenContent(
            state = state,
            onAction = onAction,
        )


        /*
                val fallbackFocusRequester = remember { FocusRequester() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(fallbackFocusRequester)
                        .verticalScroll(rememberScrollState())
                        .focusGroup(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Favorites TBD")
                    LazyRow(contentPadding = PaddingValues(start = 16.dp)) {
                        items(count = 20) { index ->
                            Card(
                                modifier = Modifier.ifElse(
                                    index == 0,
                                    Modifier.focusRequester(fallbackFocusRequester),
                                ),
                                onClick = {},
                            ) {
                                Icon(
                                    modifier = Modifier.size(94.dp),
                                    imageVector = Icons.Default.Accessibility,
                                    contentDescription = null,
                                )

                                Text("Favorites TBD")
                            }
                        }
                    }

                    LazyRow(contentPadding = PaddingValues(start = 16.dp)) {
                        items(count = 20) { index ->
                            Card(
                                modifier = Modifier,
                                onClick = {},
                            ) {
                                Icon(
                                    modifier = Modifier.size(94.dp),
                                    imageVector = Icons.Default.Accessibility,
                                    contentDescription = null,
                                )

                                Text("Favorites TBD")
                            }
                        }
                    }
                }
        */
    }
}