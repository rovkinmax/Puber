package com.kino.puber.ui.feature.contentlist.model

import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.CARTOON_GENRE_ID
import com.kino.puber.ui.feature.main.model.TabType

internal object TabTypeConfig {

    fun heroConfigsFor(tabType: TabType): List<SectionConfig> = when (tabType) {
        TabType.Cartoons -> heroConfigs(
            idSuffix = "cartoon",
            genre = CARTOON_GENRE_ID,
            animeFilterMode = AnimeFilterMode.Exclude,
        )
        TabType.Anime -> heroConfigs(
            idSuffix = "anime",
            genre = ANIME_GENRE_ID,
            animeFilterMode = AnimeFilterMode.Only,
        )
        else -> emptyList()
    }

    fun sectionsFor(tabType: TabType): List<SectionConfig> = when (tabType) {
        TabType.Movies -> standardSections(
            type = "movie",
            animeFilterMode = AnimeFilterMode.FollowPreference,
        )
        TabType.Series -> standardSections(
            type = "serial",
            animeFilterMode = AnimeFilterMode.FollowPreference,
        )
        TabType.Concerts -> standardSections(type = "concert")
        TabType.DocMovies -> standardSections(type = "documovie")
        TabType.DocSeries -> sectionsWithout4k(type = "docuserial")
        TabType.TvShows -> sectionsWithout4k(type = "tvshow")
        TabType.Cartoons -> cartoonSections()
        TabType.Anime -> animeSections()
        TabType.For4k -> for4kSections()
        else -> emptyList()
    }

    private fun standardSections(
        type: String,
        animeFilterMode: AnimeFilterMode = AnimeFilterMode.None,
    ): List<SectionConfig> = listOf(
        SectionConfig(
            id = "fresh_$type",
            title = "Новинки",
            type = type,
            shortcut = "fresh",
            animeFilterMode = animeFilterMode,
        ),
        SectionConfig(
            id = "popular_$type",
            title = "Популярные",
            type = type,
            shortcut = "popular",
            animeFilterMode = animeFilterMode,
        ),
        SectionConfig(
            id = "hot_$type",
            title = "Горячие",
            type = type,
            shortcut = "hot",
            animeFilterMode = animeFilterMode,
        ),
        SectionConfig(
            id = "4k_$type",
            title = "4K",
            type = type,
            quality = "4k",
            sort = "updated-",
            animeFilterMode = animeFilterMode,
        ),
        SectionConfig(
            id = "all_$type",
            title = "Все",
            type = type,
            sort = "updated-",
            animeFilterMode = animeFilterMode,
        ),
    )

    private fun heroConfigs(
        idSuffix: String,
        genre: Int,
        animeFilterMode: AnimeFilterMode,
    ): List<SectionConfig> = listOf("movie", "serial").map { type ->
        SectionConfig(
            id = "hero_hot_${idSuffix}_$type",
            title = "",
            type = type,
            shortcut = "hot",
            genre = genre.toString(),
            animeFilterMode = animeFilterMode,
        )
    }

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
            genre = CARTOON_GENRE_ID.toString(),
            sort = "views-",
            animeFilterMode = AnimeFilterMode.Exclude,
        ),
        SectionConfig(
            id = "all_cartoon",
            title = "Все",
            genre = CARTOON_GENRE_ID.toString(),
            sort = "updated-",
            animeFilterMode = AnimeFilterMode.Exclude,
        ),
    )

    private fun animeSections(): List<SectionConfig> = listOf(
        SectionConfig(
            id = "popular_anime",
            title = "Популярные",
            genre = ANIME_GENRE_ID.toString(),
            sort = "views-",
            animeFilterMode = AnimeFilterMode.Only,
        ),
        SectionConfig(
            id = "all_anime",
            title = "Все",
            genre = ANIME_GENRE_ID.toString(),
            sort = "updated-",
            animeFilterMode = AnimeFilterMode.Only,
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
