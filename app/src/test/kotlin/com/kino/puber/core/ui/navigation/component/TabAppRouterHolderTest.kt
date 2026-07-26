package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class TabAppRouterHolderTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun logicalTabKeyOwnsOneRouterAcrossRefreshGenerations() {
        val holder = TabAppRouterHolder(mockk<Screens>(relaxed = true))
        val initial = holder.getOrCreate(
            key = "Tab:HistoryScreen",
            initialContentInstanceKey = "Tab:HistoryScreen",
        )
        val refreshed = holder.getOrCreate(
            key = "Tab:HistoryScreen",
            initialContentInstanceKey = "Tab:HistoryScreen:refresh_1",
        )
        val other = holder.getOrCreate(
            key = "Tab:HomeScreen",
            initialContentInstanceKey = "Tab:HomeScreen",
        )

        assertSame(initial, refreshed)
        assertNotSame(initial, other)

        holder.dispose()
    }
}
