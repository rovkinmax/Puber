package com.kino.puber.core.ui.navigation

import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.player.model.PlayerStartMode

interface Screens {
    fun auth(): PuberScreen

    fun main(): PuberScreen

    fun search(): PuberScreen

    fun actorItems(actorName: String): PuberScreen

    fun home(): PuberScreen

    fun history(presentation: HistoryPresentation): PuberScreen

    fun collections(): PuberScreen

    fun bookmarks(): PuberScreen

    fun favorites(): PuberScreen

    fun deviceSettings(): PuberScreen

    fun contentList(tabType: TabType): PuberScreen

    fun underDevelopment(): PuberScreen

    fun details(itemId: Int): PuberScreen

    fun details(itemId: Int, initialEpisode: DetailsEpisodeTarget): PuberScreen

    fun player(
        itemId: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        videoNumber: Int? = null,
        startMode: PlayerStartMode = PlayerStartMode.ResumeIfAvailable,
    ): PuberScreen
}
