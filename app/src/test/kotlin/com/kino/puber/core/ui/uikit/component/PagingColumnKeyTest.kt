package com.kino.puber.core.ui.uikit.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PagingColumnKeyTest {

    @Test
    fun itemKey_preservesIdentityAcrossReorderAndPayloadReplacement() {
        val initialItems = listOf(
            TestItem(id = "first", payload = "old first"),
            TestItem(id = "second", payload = "old second"),
        )
        val updatedItems = listOf(
            TestItem(id = "second", payload = "new second"),
            TestItem(id = "first", payload = "new first"),
        )
        val itemKey: (TestItem) -> Any = TestItem::id
        val initialKeys = initialItems.associate { item ->
            item.id to resolvePagingColumnItemKey(item, itemKey)
        }

        assertNotEquals(initialItems[0].payload, updatedItems[1].payload)
        assertNotEquals(initialItems[1].payload, updatedItems[0].payload)
        assertEquals("new first", updatedItems[1].payload)
        assertEquals("new second", updatedItems[0].payload)
        assertEquals(
            listOf("second", "first"),
            updatedItems.map { item -> resolvePagingColumnItemKey(item, itemKey) },
        )
        assertEquals(
            initialKeys["first"],
            resolvePagingColumnItemKey(updatedItems[1], itemKey),
        )
        assertEquals(
            initialKeys["second"],
            resolvePagingColumnItemKey(updatedItems[0], itemKey),
        )
    }

    private data class TestItem(
        val id: String,
        val payload: String,
    )
}
