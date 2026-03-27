package com.kino.puber.ui.feature.contentlist.model

import com.kino.puber.ui.feature.main.model.TabType

internal object TabTypeConfig {

    fun sectionsFor(tabType: TabType): List<SectionConfig> = when (tabType) {
        TabType.Movies -> standardSections(type = "movie")
        TabType.Series -> standardSections(type = "serial")
        TabType.Concerts -> standardSections(type = "concert")
        TabType.DocMovies -> standardSections(type = "documovie")
        TabType.DocSeries -> sectionsWithout4k(type = "docuserial")
        TabType.TvShows -> sectionsWithout4k(type = "tvshow")
        TabType.Cartoons -> cartoonSections()
        TabType.For4k -> for4kSections()
        else -> emptyList()
    }

    private fun standardSections(type: String): List<SectionConfig> = listOf(
        SectionConfig(
            id = "fresh_$type",
            title = "Новинки",
            type = type,
            shortcut = "fresh",
        ),
        SectionConfig(
            id = "popular_$type",
            title = "Популярные",
            type = type,
            shortcut = "popular",
        ),
        SectionConfig(
            id = "hot_$type",
            title = "Горячие",
            type = type,
            shortcut = "hot",
        ),
        SectionConfig(
            id = "4k_$type",
            title = "4K",
            type = type,
            quality = "4k",
            sort = "updated-",
        ),
        SectionConfig(
            id = "all_$type",
            title = "Все",
            type = type,
            sort = "updated-",
        ),
    )

    private fun sectionsWithout4k(type: String): List<SectionConfig> = listOf(
        SectionConfig(
            id = "fresh_$type",
            title = "Новинки",
            type = type,
            shortcut = "fresh",
        ),
        SectionConfig(
            id = "popular_$type",
            title = "Популярные",
            type = type,
            shortcut = "popular",
        ),
        SectionConfig(
            id = "hot_$type",
            title = "Горячие",
            type = type,
            shortcut = "hot",
        ),
        SectionConfig(
            id = "all_$type",
            title = "Все",
            type = type,
            sort = "updated-",
        ),
    )

    private fun cartoonSections(): List<SectionConfig> = listOf(
        SectionConfig(
            id = "popular_cartoon",
            title = "Популярные",
            genre = "23",
            sort = "views-",
        ),
        SectionConfig(
            id = "all_cartoon",
            title = "Все",
            genre = "23",
            sort = "updated-",
        ),
    )

    private fun for4kSections(): List<SectionConfig> = listOf(
        SectionConfig(
            id = "popular_4k",
            title = "Популярные",
            quality = "4k",
            sort = "views-",
        ),
        SectionConfig(
            id = "all_4k",
            title = "Все",
            quality = "4k",
            sort = "updated-",
        ),
    )
}
