package com.kino.puber.data.api.models

const val CARTOON_GENRE_ID = 23
const val ANIME_GENRE_ID = 25

fun Item.isAnime(): Boolean = genres.orEmpty().any { genre ->
    genre.id == ANIME_GENRE_ID
}
