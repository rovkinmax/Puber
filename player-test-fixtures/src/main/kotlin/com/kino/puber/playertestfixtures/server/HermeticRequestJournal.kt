package com.kino.puber.playertestfixtures.server

import java.util.concurrent.atomic.AtomicLong
import mockwebserver3.RecordedRequest

/**
 * Bounded request evidence. Only transport metadata needed by tests is kept;
 * authorization, cookies, and query values are intentionally excluded.
 */
class HermeticRequestJournal(
    private val maxEntries: Int = 256,
) {
    private val lock = Any()
    private val nextId = AtomicLong()
    private val entries = ArrayDeque<RequestEntry>()
    private val matched = linkedMapOf<String, Int>()
    private var active = 0
    private var generation = 0L

    val generationId: Long
        get() = synchronized(lock) { generation }

    val activeRequestCount: Int
        get() = synchronized(lock) { active }

    fun reset() = synchronized(lock) {
        check(active == 0) { "Cannot reset with $active active request(s)" }
        entries.clear()
        matched.clear()
        active = 0
        generation += 1
    }

    fun begin(request: RecordedRequest, routeId: String?): Long =
        synchronized(lock) {
            val id = nextId.incrementAndGet()
            routeId?.let { matched[it] = (matched[it] ?: 0) + 1 }
            active += 1
            add(
                RequestEntry(
                    id = id,
                    method = request.method,
                    path = request.url.encodedPath.ifBlank { "/" },
                    queryKeys = request.url.queryParameterNames.toList().sorted(),
                    headers = selectedHeaders(request),
                    range = request.headers["Range"],
                    routeId = routeId,
                    outcome = null,
                ),
            )
            id
        }

    fun complete(requestId: Long, outcome: ResponseOutcome) = synchronized(lock) {
        val index = entries.indexOfFirst { it.id == requestId }
        if (index >= 0 && entries[index].outcome == null) {
            entries[index] = entries[index].copy(outcome = outcome)
            active = (active - 1).coerceAtLeast(0)
        }
    }

    fun matchedCount(routeId: String): Int = synchronized(lock) {
        matched[routeId] ?: 0
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            generationId = generation,
            entries = entries.toList(),
            matchedRoutes = matched.toMap(),
            activeRequestCount = active,
        )
    }

    private fun add(entry: RequestEntry) {
        entries.addLast(entry)
        while (entries.size > maxEntries) entries.removeFirst()
    }

    private fun selectedHeaders(request: RecordedRequest): Map<String, String> =
        SELECTED_HEADERS.mapNotNull { name ->
            request.headers[name]?.let { name to it.take(MAX_HEADER_LENGTH) }
        }.toMap()

    data class Snapshot(
        val generationId: Long,
        val entries: List<RequestEntry>,
        val matchedRoutes: Map<String, Int>,
        val activeRequestCount: Int,
    ) {
        val unknownRequests: List<RequestEntry>
            get() = entries.filter { it.routeId == null }
    }

    data class RequestEntry(
        val id: Long,
        val method: String,
        val path: String,
        val queryKeys: List<String>,
        val headers: Map<String, String>,
        val range: String?,
        val routeId: String?,
        val outcome: ResponseOutcome?,
    )

    private companion object {
        const val MAX_HEADER_LENGTH = 256
        val SELECTED_HEADERS = listOf(
            "Accept",
            "Cache-Control",
            "Content-Type",
            "Pragma",
        )
    }
}

sealed interface ResponseOutcome {
    data class Completed(val status: Int) : ResponseOutcome
    data object UnknownRoute : ResponseOutcome
}
