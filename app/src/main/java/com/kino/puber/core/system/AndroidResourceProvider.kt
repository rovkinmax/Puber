package com.kino.puber.core.system

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat

class AndroidResourceProvider(private val context: Context) : ResourceProvider {
    override fun getString(resId: Int): String = context.getString(resId)
    override fun getString(resId: Int, vararg arg: Any): String = context.getString(resId, *arg)
    override fun getColor(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
    override fun getStringArray(resId: Int): Array<String> = context.resources.getStringArray(resId)
    override fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String {
        return context.resources.getQuantityString(resId, quantity, *args)
    }

    override fun getImageVector(resId: Int): ImageVector {
        val res = context.resources
        val theme = context.theme
        return ImageVector.vectorResource(
            theme = theme, res = res,
            resId = resId,
        )
    }
}