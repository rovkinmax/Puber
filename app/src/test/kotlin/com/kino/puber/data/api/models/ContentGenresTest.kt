package com.kino.puber.data.api.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentGenresTest {

    @Test
    fun isAnime_returnsTrue_whenAnimeGenreIsPresent() {
        assertTrue(itemWithGenres(ANIME_GENRE_ID).isAnime())
    }

    @Test
    fun isAnime_returnsTrue_whenCartoonAndAnimeGenresArePresent() {
        assertTrue(itemWithGenres(CARTOON_GENRE_ID, ANIME_GENRE_ID).isAnime())
    }

    @Test
    fun isAnime_returnsFalse_whenAnimeGenreIsAbsent() {
        assertFalse(itemWithGenres(CARTOON_GENRE_ID).isAnime())
    }

    @Test
    fun isAnime_returnsFalse_whenGenresAreMissing() {
        assertFalse(itemWithGenres().isAnime())
    }

    private fun itemWithGenres(vararg genreIds: Int) = Item(
        id = 1,
        title = "Item",
        type = ItemType.MOVIE,
        genres = genreIds
            .map { id -> Genre(id = id, title = "Genre $id") }
            .takeIf(List<Genre>::isNotEmpty),
    )
}
