package com.kino.puber.ui.feature.details.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal class DetailsScreenParams(
    val itemId: Int,
    val initialEpisode: DetailsEpisodeTarget? = null,
) : Parcelable

@Parcelize
data class DetailsEpisodeTarget(
    val seasonNumber: Int,
    val episodeNumber: Int,
) : Parcelable
