package com.kino.puber.core.content

enum class ContentChangeType {
    PlaybackProgress,
    Watched,
    Bookmark,
    Watchlist,
    History,
}

data class ContentChange(
    val itemId: Int,
    val types: Set<ContentChangeType>,
) {
    constructor(itemId: Int, type: ContentChangeType) : this(itemId, setOf(type))
}

data class ContentChangeSet(
    val changes: Map<Int, Set<ContentChangeType>>,
) {
    val isEmpty: Boolean
        get() = changes.isEmpty()

    val types: Set<ContentChangeType>
        get() = changes.values.flatten().toSet()

    val itemIds: Set<Int>
        get() = changes.keys

    fun affectsItem(itemId: Int): Boolean = changes.containsKey(itemId)

    fun merge(change: ContentChange): ContentChangeSet {
        if (change.types.isEmpty()) return this

        val itemTypes = changes[change.itemId].orEmpty() + change.types
        return ContentChangeSet(changes + (change.itemId to itemTypes))
    }

    fun merge(other: ContentChangeSet): ContentChangeSet {
        return other.changes.entries.fold(this) { acc, (itemId, types) ->
            acc.merge(ContentChange(itemId, types))
        }
    }

    companion object {
        fun empty(): ContentChangeSet = ContentChangeSet(emptyMap())

        fun single(itemId: Int, type: ContentChangeType): ContentChangeSet {
            return ContentChangeSet(mapOf(itemId to setOf(type)))
        }
    }
}
