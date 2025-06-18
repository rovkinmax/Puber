package com.kino.puber.core.collections

fun <T> MutableList<T>.replace(
    new: T,
    onlyFirst: Boolean = true,
    equally: EquallyFunction<T>
): Boolean {
    return replace(
        predicate = { equally.isItemTheSame(it, new) },
        transform = { new },
        onlyFirst = onlyFirst,
    )
}

fun <T> MutableList<T>.replace(
    predicate: (T) -> Boolean,
    transform: (T) -> T,
    onlyFirst: Boolean = true
): Boolean {
    var isReplaced = false
    forEachIndexed { index, old ->
        if (predicate(old)) {
            set(index, transform(old))
            isReplaced = true
            if (onlyFirst) {
                return true
            }
        }
    }
    return isReplaced
}

fun <T> MutableList<T>.clearAndAddAll(newList: List<T>): Boolean {
    clear()
    return addAll(newList)
}

/**
 * Replace element by predicate or insert in to end of list
 */
fun <T> MutableList<T>.replaceOrInsert(new: T, equally: EquallyFunction<T>) {
    synchronized(this) {
        for (i in indices) {
            if (equally.isItemTheSame(this[i], new)) {
                this[i] = new
                return
            }
        }
        this.add(new)
    }
}

fun <T> MutableList<T>.replaceOrInsertAll(items: List<T>, equally: EquallyFunction<T>) {
    items.forEach { replaceOrInsert(it, equally) }
}

/**
 * Replace element by predicate or insert in to start of list
 */
fun <T> MutableList<T>.replaceOrInsertAtStart(new: T, equally: EquallyFunction<T>) {
    var isReplaced = false
    forEachIndexed { index, old ->
        if (equally.isItemTheSame(old, new)) {
            set(index, new)
            isReplaced = true
        }
    }

    if (isReplaced.not()) {
        add(0, new)
    }
}

fun <T> MutableList<T>.removeAllEqually(item: T, equally: EquallyFunction<T>): Boolean {
    return removeAll { equally.isItemTheSame(it, item) }
}

fun <T> List<T>.findEqually(item: T, equally: EquallyFunction<T>): T? {
    return find { equally.isItemTheSame(it, item) }
}

fun <T> Iterable<T>.zipToPairs(): List<Pair<T, T?>> {
    return zipToPairs { a, b -> a to b }
}

fun <T, R> Iterable<T>.zipToPairs(transform: (a: T, b: T?) -> R): List<R> {
    val iterator = iterator()
    if (iterator.hasNext().not()) {
        return emptyList()
    }
    val result = mutableListOf<R>()
    while (iterator.hasNext()) {
        val current = iterator.next()
        val next = if (iterator.hasNext()) iterator.next() else null
        result.add(transform(current, next))
    }

    return result
}

inline fun <T : Any?> List<T>.fastForEach(action: (T) -> Unit) {
    for (i in 0 until size) {
        action(this[i])
    }
}

inline fun <T : Any?, R : Any?> List<T>.fastMap(transform: (T) -> R): List<R> {
    val newList = ArrayList<R>(size)
    fastForEach {
        newList.add(transform(it))
    }
    return newList
}


fun <K, V> Map<K, V?>.filterNotNull(): Map<K, V> {
    val newMap = mutableMapOf<K, V>()
    entries.forEach { entry ->
        if (entry.key != null && entry.value != null) {
            newMap[entry.key!!] = entry.value!!
        }
    }

    return newMap
}

fun <T> List<T>.next(element: T): T = this[this.indexOf(element) + 1]
fun <T> List<T>.isLastElement(element: T) = element == this.last()

fun <T> Collection<T>.firstOrNullIndexed(predicate: (index: Int, element: T) -> Boolean): T? {
    for (index in indices) {
        val element = this.elementAt(index)
        if (predicate(index, element)) {
            return element
        }
    }

    return null
}

fun <T> Collection<T>.containsAnyOf(other: Collection<T>): Boolean {
    other.forEach {
        if (contains(it)) {
            return true
        }
    }
    return false
}

fun <T> MutableList<T>.addAll(vararg items: T) {
    addAll(items)
}

fun <T> List<T>.mergeWith(other: List<T>, equally: EquallyFunction<T>): List<T> {
    val result = mutableListOf<T>()
    result.addAll(this)
    other.forEach { otherItem ->
        result.replaceOrInsert(otherItem, equally)
    }
    return result
}