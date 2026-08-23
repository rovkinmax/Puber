package com.kino.puber.ui.feature.episodeschedule.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EpisodeScheduleScreenParams(
    val itemId: Int,
    val title: String,
    val imdbId: String,
) : Parcelable
