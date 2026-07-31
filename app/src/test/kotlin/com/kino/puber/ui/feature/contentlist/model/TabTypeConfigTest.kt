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
    fun heroConfigs_useHotShortcutAndDistinctGenreFilters() {
        val cartoons = TabTypeConfig.heroConfigsFor(TabType.Cartoons)
        val anime = TabTypeConfig.heroConfigsFor(TabType.Anime)

        assertEquals(listOf("movie", "serial"), cartoons.map(SectionConfig::type))
        cartoons.forEach { config ->
            assertEquals("hot", config.shortcut)
            assertEquals(CARTOON_GENRE_ID.toString(), config.genre)
            assertEquals(AnimeFilterMode.Exclude, config.animeFilterMode)
        }
        assertEquals(
            listOf("hero_hot_cartoon_movie", "hero_hot_cartoon_serial"),
            cartoons.map(SectionConfig::id),
        )

        assertEquals(listOf("movie", "serial"), anime.map(SectionConfig::type))
        anime.forEach { config ->
            assertEquals("hot", config.shortcut)
            assertEquals(ANIME_GENRE_ID.toString(), config.genre)
            assertEquals(AnimeFilterMode.Only, config.animeFilterMode)
        }
        assertEquals(
            listOf("hero_hot_anime_movie", "hero_hot_anime_serial"),
            anime.map(SectionConfig::id),
        )

        assertTrue(
            cartoons.none { it.id in TabTypeConfig.sectionsFor(TabType.Cartoons).map(SectionConfig::id) },
        )
        assertTrue(
            anime.none { it.id in TabTypeConfig.sectionsFor(TabType.Anime).map(SectionConfig::id) },
        )
    }

    @Test
    fun heroConfig_isAbsentForUnrelatedTabs() {
        TabType.entries
            .filterNot { it == TabType.Cartoons || it == TabType.Anime }
            .forEach { tabType ->
                assertEquals(
                    emptyList<SectionConfig>(),
                    TabTypeConfig.heroConfigsFor(tabType),
                    "$tabType unexpectedly generated a hero config",
                )
            }
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
