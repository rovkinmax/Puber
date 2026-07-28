package com.kino.puber.ui.feature.contentlist.model

import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.CARTOON_GENRE_ID
import com.kino.puber.ui.feature.main.model.TabType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabTypeConfigTest {

    @Test
    fun moviesAndSeriesSections_followAnimePreference() {
        listOf(TabType.Movies, TabType.Series).forEach { tabType ->
            val sections = TabTypeConfig.sectionsFor(tabType)

            assertTrue(sections.isNotEmpty())
            assertEquals(
                setOf(AnimeFilterMode.FollowPreference),
                sections.map(SectionConfig::animeFilterMode).toSet(),
            )
        }
    }

    @Test
    fun cartoonsSections_useCartoonGenreAndAlwaysExcludeAnime() {
        val sections = TabTypeConfig.sectionsFor(TabType.Cartoons)

        assertEquals(listOf("popular_cartoon", "all_cartoon"), sections.map(SectionConfig::id))
        assertEquals(
            setOf(CARTOON_GENRE_ID.toString()),
            sections.mapNotNull(SectionConfig::genre).toSet(),
        )
        assertEquals(
            setOf(AnimeFilterMode.Exclude),
            sections.map(SectionConfig::animeFilterMode).toSet(),
        )
    }

    @Test
    fun animeSections_useAnimeGenreAndOnlyMode() {
        val sections = TabTypeConfig.sectionsFor(TabType.Anime)

        assertEquals(listOf("popular_anime", "all_anime"), sections.map(SectionConfig::id))
        assertEquals(listOf("views-", "updated-"), sections.map(SectionConfig::sort))
        assertEquals(
            setOf(ANIME_GENRE_ID.toString()),
            sections.mapNotNull(SectionConfig::genre).toSet(),
        )
        assertEquals(
            setOf(AnimeFilterMode.Only),
            sections.map(SectionConfig::animeFilterMode).toSet(),
        )
    }

    @Test
    fun unrelatedCatalogTabs_doNotFilterAnime() {
        val unfilteredTabs = listOf(
            TabType.For4k,
            TabType.Concerts,
            TabType.DocMovies,
            TabType.DocSeries,
            TabType.TvShows,
        )

        unfilteredTabs.forEach { tabType ->
            val sections = TabTypeConfig.sectionsFor(tabType)

            assertTrue(sections.isNotEmpty())
            assertEquals(
                setOf(AnimeFilterMode.None),
                sections.map(SectionConfig::animeFilterMode).toSet(),
            )
        }
    }

    @Test
    fun nonCatalogTabs_haveNoGeneratedSections() {
        val configuredTabs = setOf(
            TabType.Movies,
            TabType.Series,
            TabType.Cartoons,
            TabType.Anime,
            TabType.For4k,
            TabType.Concerts,
            TabType.DocMovies,
            TabType.DocSeries,
            TabType.TvShows,
        )

        TabType.entries
            .filterNot(configuredTabs::contains)
            .forEach { tabType ->
                assertTrue(
                    TabTypeConfig.sectionsFor(tabType).isEmpty(),
                    "$tabType unexpectedly generated catalog sections",
                )
            }
    }
}
