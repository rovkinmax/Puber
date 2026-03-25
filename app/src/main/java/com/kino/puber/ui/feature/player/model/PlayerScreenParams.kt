package com.kino.puber.ui.feature.player.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal class PlayerScreenParams(
    val itemId: Int,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) : Parcelable
