package com.kino.puber.ui.feature.search.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class SearchScreenParams(
    val mode: SearchMode = SearchMode.Title,
) : Parcelable {

    @Parcelize
    internal sealed interface SearchMode : Parcelable {
        @Parcelize
        data object Title : SearchMode

        @Parcelize
        data class Actor(
            val actorQuery: String,
        ) : SearchMode
    }
}
