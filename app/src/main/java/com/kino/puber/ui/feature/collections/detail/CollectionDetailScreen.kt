package com.kino.puber.ui.feature.collections.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import kotlinx.parcelize.Parcelize
import org.koin.compose.koinInject
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class CollectionDetailScreen(
    private val collectionId: Int,
    private val collectionTitle: String,
) : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::CollectionInteractor)
            scoped { VideoItemUIMapper(get()) }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val interactor = koinInject<CollectionInteractor>()
        val mapper = koinInject<VideoItemUIMapper>()
        val router = koinInject<AppRouter>()

        var items by remember { mutableStateOf<List<VideoItemUIState>?>(null) }

        LaunchedEffect(collectionId) {
            interactor.getCollectionItems(collectionId).onSuccess { response ->
                items = mapper.mapShortItemList(response.items)
            }
        }

        Column(Modifier.fillMaxSize()) {
            Text(
                text = collectionTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            val currentItems = items
            if (currentItems == null) {
                FullScreenProgressIndicator()
            } else if (currentItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Пусто")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(currentItems, key = { _, item -> item.id }) { _, item ->
                        VideoItemHorizontal(
                            state = item,
                            onClick = { router.navigateTo(router.screens.details(item.id)) },
                        )
                    }
                }
            }
        }
    }
}
