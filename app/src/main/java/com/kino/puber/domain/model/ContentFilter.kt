package com.kino.puber.domain.model

import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Genre

data class ContentFilter(
    val genre: Genre? = null,
    val country: Country? = null,
    val sort: SortField = SortField.UPDATED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val yearRange: IntRange? = null,
    val kinopoiskRating: ClosedFloatingPointRange<Float>? = null,
    val imdbRating: ClosedFloatingPointRange<Float>? = null,
    val quality: String? = null,
    val finished: Boolean? = null,
)

enum class SortField(val apiValue: String) {
    UPDATED("updated"),
    YEAR("year"),
    CREATED("created"),
    RATING("rating"),
    VIEWS("views"),
    KINOPOISK("kinopoisk_rating"),
    IMDB("imdb_rating"),
}

enum class SortDirection(val apiValue: String) {
    DESC("-"),
    ASC(""),
}
