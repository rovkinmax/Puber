package com.kino.puber.core.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentChangeSetTest {

    @Test
    fun empty_hasNoChanges() {
        val changes = ContentChangeSet.empty()

        assertTrue(changes.isEmpty)
        assertFalse(changes.affectsItem(42))
        assertEquals(emptySet<ContentChangeType>(), changes.types)
    }

    @Test
    fun single_marksItemAndType() {
        val changes = ContentChangeSet.single(42, ContentChangeType.PlaybackProgress)

        assertFalse(changes.isEmpty)
        assertTrue(changes.affectsItem(42))
        assertEquals(setOf(ContentChangeType.PlaybackProgress), changes.changes[42])
        assertEquals(setOf(ContentChangeType.PlaybackProgress), changes.types)
    }

    @Test
    fun merge_combinesTypesForSameItem() {
        val changes = ContentChangeSet
            .single(42, ContentChangeType.PlaybackProgress)
            .merge(ContentChange(42, ContentChangeType.Watched))

        assertEquals(
            setOf(ContentChangeType.PlaybackProgress, ContentChangeType.Watched),
            changes.changes[42],
        )
    }

    @Test
    fun merge_combinesMultipleItems() {
        val changes = ContentChangeSet
            .single(42, ContentChangeType.Watchlist)
            .merge(ContentChangeSet.single(7, ContentChangeType.Bookmark))

        assertTrue(changes.affectsItem(42))
        assertTrue(changes.affectsItem(7))
        assertEquals(setOf(42, 7), changes.itemIds)
        assertEquals(setOf(ContentChangeType.Watchlist), changes.changes[42])
        assertEquals(setOf(ContentChangeType.Bookmark), changes.changes[7])
    }
}
