package com.kino.puber.core.ui.navigation

import com.kino.puber.ui.feature.main.model.TabType

interface Screens {
    fun auth(): PuberScreen

    fun main(): PuberScreen

    fun search(): PuberScreen

    fun favorites(): PuberScreen

    fun deviceSettings(): PuberScreen

    fun contentList(tabType: TabType): PuberScreen

    fun underDevelopment(): PuberScreen

    fun details(itemId: Int): PuberScreen

    fun player(itemId: Int, seasonNumber: Int? = null, episodeNumber: Int? = null): PuberScreen
}