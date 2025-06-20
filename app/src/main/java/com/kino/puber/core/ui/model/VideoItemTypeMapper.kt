package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.data.api.models.ItemType

class VideoItemTypeMapper(private val resources: ResourceProvider) {
    fun map(type: ItemType): String {
        return when (type) {
            ItemType.MOVIE -> resources.getString(R.string.item_type_movie)
            ItemType.SERIAL -> resources.getString(R.string.item_type_serial)
            ItemType.TV_SHOW -> resources.getString(R.string.item_type_tv_show)
            ItemType.F4K -> resources.getString(R.string.item_type_f4k)
            ItemType.D3D -> resources.getString(R.string.item_type_d3d)
            ItemType.CONCERT -> resources.getString(R.string.item_type_concert)
            ItemType.DOCU_MOVIE -> resources.getString(R.string.item_type_docu_movie)
            ItemType.DOCU_SERIAL -> resources.getString(R.string.item_type_docu_serial)
            else -> ""
        }
    }
}