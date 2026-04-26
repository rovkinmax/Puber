package com.kino.puber.ui.feature.contentlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.content.ContentListScreenContent
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.contentlist.vm.ContentListVM
import com.kino.puber.ui.feature.contentlist.vm.SectionVM
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class ContentListScreen(
    private val tabType: TabType,
) : PuberScreen {

    override val key: ScreenKey = "ContentListScreen_${tabType.name}"

    private val sections get() = TabTypeConfig.sectionsFor(tabType)

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::ContentListInteractor)
            scoped { VideoItemUIMapper(get()) }
            viewModel {
                ContentListVM(
                    router = get(),
                    interactor = get(),
                    mapper = get(),
                    genreInteractor = get(),
                    navPrefs = get(),
                    contentType = sections.firstOrNull()?.type,
                )
            }

            sections.forEach { sec ->
                scoped(named(sec.id)) {
                    SectionVM(
                        paginator = Paginator.Store { old, new -> old.id == new.id },
                        config = sec,
                        interactor = get(),
                        mapper = get(),
                        router = get(),
                        errorHandler = get(),
                    )
                }
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val contentListVm = puberViewModel<ContentListVM>()
        val state by contentListVm.collectViewState()
        val onAction = remember(contentListVm) { contentListVm::onAction }
        ContentListScreenContent(
            state = state,
            sections = sections,
            onAction = onAction,
        )
    }
}
