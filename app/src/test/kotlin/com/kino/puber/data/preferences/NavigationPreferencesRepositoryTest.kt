package com.kino.puber.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.ui.feature.main.model.TabType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TOP_TABS_KEY = "toptabs_tabs_visible"
private const val SIDE_DRAWER_KEY = "drawer_tabs_visible"
private const val TOP_TABS_SCHEMA_VERSION_KEY = "toptabs_schema_version"
private const val HISTORY_SCHEMA_VERSION = 1

internal class NavigationPreferencesRepositoryTest {

    @Test
    fun defaultSideDrawer_usesEnabledDeclarationOrderWithHistory() {
        val fixture = fixture()

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.SideDrawer)

        assertEquals(
            listOf(
                TabType.Search,
                TabType.Favourites,
                TabType.History,
                TabType.Movies,
                TabType.Series,
                TabType.Cartoons,
                TabType.For4k,
                TabType.Concerts,
                TabType.DocMovies,
                TabType.DocSeries,
                TabType.TvShows,
                TabType.Settings,
            ),
            tabs,
        )
        assertEquals(TabType.entries.filter(TabType::enabled), tabs)
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun storedSideDrawerSelection_isReturnedWithoutInsertionOrReordering() {
        val fixture = fixture(
            storedDrawerTabs = "Movies,Favourites,Settings",
        )

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.SideDrawer)

        assertEquals(
            listOf(TabType.Movies, TabType.Favourites, TabType.Settings),
            tabs,
        )
        assertFalse(TabType.History in tabs)
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun defaultTopTabs_placeHistoryAfterCollectionsAndPersistSchema() {
        val fixture = fixture()

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Collections,
                TabType.History,
            ),
            tabs,
        )
        assertEquals(1, fixture.preferences.transactions.size)
        assertEquals(
            setOf(TOP_TABS_KEY, TOP_TABS_SCHEMA_VERSION_KEY),
            fixture.preferences.transactions.single().keys,
        )
        assertEquals(HISTORY_SCHEMA_VERSION, fixture.preferences.values[TOP_TABS_SCHEMA_VERSION_KEY])
    }

    @Test
    fun migrationNormalizesRequiredTabsAndDuplicateHistory() {
        val fixture = fixture(
            storedTabs = "Movies,Search,History,Series,History,Settings,Collections",
        )

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Collections,
                TabType.History,
            ),
            tabs,
        )
    }

    @Test
    fun migrationAppendsHistoryWhenCollectionsAreMissing() {
        val fixture = fixture(storedTabs = "History,Series,Movies")

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(TabType.Home, TabType.Series, TabType.Movies, TabType.History),
            tabs,
        )
    }

    @Test
    fun migrationIgnoresUnknownAndMalformedTabNames() {
        val fixture = fixture(storedTabs = "Unknown,Movies,, Series ,DocMovies,broken")

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.DocMovies, TabType.History),
            tabs,
        )
    }

    @Test
    fun migrationRunsOnlyOnceAcrossRepeatedReads() {
        val fixture = fixture(storedTabs = "Movies,Collections")

        val first = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)
        val transactionsAfterFirstRead = fixture.preferences.transactions.size
        val second = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(first, second)
        assertEquals(1, transactionsAfterFirstRead)
        assertEquals(transactionsAfterFirstRead, fixture.preferences.transactions.size)
    }

    @Test
    fun userCanRemoveHistoryAfterVersionOneWhileSearchAndSettingsStayOutside() {
        val fixture = fixture()
        fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        fixture.repository.setVisibleTabs(
            mode = NavigationMode.TopTabs,
            tabs = listOf(
                TabType.Search,
                TabType.Home,
                TabType.Movies,
                TabType.Settings,
            ),
        )
        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(listOf(TabType.Home, TabType.Movies), tabs)
        assertFalse(TabType.History in tabs)
        assertFalse(TabType.Search in tabs)
        assertFalse(TabType.Settings in tabs)
    }

    private fun fixture(
        storedTabs: String? = null,
        storedDrawerTabs: String? = null,
    ): Fixture {
        val preferences = TestPreferences()
        storedTabs?.let { preferences.values[TOP_TABS_KEY] = it }
        storedDrawerTabs?.let { preferences.values[SIDE_DRAWER_KEY] = it }
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(any(), Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        return Fixture(
            repository = NavigationPreferencesRepository(context),
            preferences = preferences,
        )
    }

    private data class Fixture(
        val repository: NavigationPreferencesRepository,
        val preferences: TestPreferences,
    )
}

private class TestPreferences {
    val values: MutableMap<String, Any?> = mutableMapOf()
    val transactions: MutableList<Map<String, Any?>> = mutableListOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val pending: MutableMap<String, Any?> = linkedMapOf()
    private val editor: SharedPreferences.Editor = mockk()

    init {
        every { sharedPreferences.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { sharedPreferences.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg()
        }
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            pending[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            pending[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.apply() } answers {
            val transaction = pending.toMap()
            values.putAll(transaction)
            transactions += transaction
            pending.clear()
        }
    }
}
