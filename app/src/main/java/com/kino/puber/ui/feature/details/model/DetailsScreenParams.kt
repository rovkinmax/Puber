package com.kino.puber.ui.feature.details.model

import android.os.Parcelable
import com.kino.puber.data.api.models.Item
import kotlinx.parcelize.Parcelize

@Parcelize
internal class DetailsScreenParams(
    val itemId: Int,
    val item: Item? = null,
) : Parcelable