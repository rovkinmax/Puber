package com.kino.puber.core.system

import androidx.compose.ui.graphics.vector.ImageVector

interface ResourceProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg arg: Any): String
    fun getColor(colorRes: Int): Int
    fun getStringArray(resId: Int): Array<String>
    fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String
    fun getImageVector(resId: Int): ImageVector
}