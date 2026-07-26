package com.kino.puber.data.api.history

internal class HistoryRequest private constructor(
    val path: String,
    val query: Map<String, String>,
    val cacheControl: String,
) {
    fun resolveUrl(baseUrl: String): String {
        require(baseUrl.isNotBlank()) { "History base URL must not be blank" }
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    companion object {
        const val NO_STORE_CACHE_CONTROL = "no-store"

        fun page(page: Int): HistoryRequest {
            require(page > 0) { "History page must be positive" }
            return HistoryRequest(
                path = "history",
                query = mapOf("page" to page.toString()),
                cacheControl = NO_STORE_CACHE_CONTROL,
            )
        }

        fun clearExactMedia(mediaId: Int): HistoryRequest {
            require(mediaId > 0) { "History media ID must be positive" }
            return HistoryRequest(
                path = "history/clear-for-media",
                query = mapOf("id" to mediaId.toString()),
                cacheControl = NO_STORE_CACHE_CONTROL,
            )
        }
    }
}
