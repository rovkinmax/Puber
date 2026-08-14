package com.kino.puber.ui.feature.contentlist.model

import android.os.Parcelable
import com.kino.puber.data.api.models.ItemType
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class SectionConfig(
    val id: String,
    val title: String,
    val type: String? = null,
    val shortcut: String? = null,
    val shortcutTypes: List<ItemType> = emptyList(),
    val sort: String? = null,
    val quality: String? = null,
    val genre: String? = null,
    val requiredGenreId: Int? = null,
    val animeFilterMode: AnimeFilterMode = AnimeFilterMode.None,
) : Parcelable

@Parcelize
internal enum class AnimeFilterMode : Parcelable {
    None,
    FollowPreference,
    Exclude,
    Only,
}
