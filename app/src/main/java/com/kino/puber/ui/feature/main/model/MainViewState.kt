package com.kino.puber.ui.feature.main.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.R
import kotlinx.parcelize.Parcelize

@Immutable
internal data class MainViewState(
    val tabs: List<MainTab> = emptyList(),
)


@Immutable
internal data class MainTab(
    val type: TabType,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
    val isSelected: Boolean = false,
    val isVisible: Boolean = false,
)

@Parcelize
internal enum class TabType(val title: Int) : Parcelable {
    Favourites(R.string.main_tabs_favorites),
    Bookmarks(R.string.main_tabs_bookmarks),
    History(R.string.main_tabs_history),
    Movies(R.string.main_tabs_movies),
    Series(R.string.main_tabs_series),
    Cartoons(R.string.main_tabs_cartoons),
    Fork(R.string.main_tabs_f4k),
    Concerts(R.string.main_tabs_concerts),
    DocMovies(R.string.main_tabs_docmovies),
    DocSeries(R.string.main_tabs_docseries),
    TvShows(R.string.main_tabs_tvshows),
    Collections(R.string.main_tabs_collections),
    SportTV(R.string.main_tabs_sport_tv),
    Settings(R.string.main_tabs_settings),
}