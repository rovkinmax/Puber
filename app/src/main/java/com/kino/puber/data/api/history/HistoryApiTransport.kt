package com.kino.puber.data.api.history

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

internal suspend fun HttpClient.fetchHistoryPage(
    page: Int,
    baseUrl: String,
): HttpResponse = get(HistoryRequest.page(page), baseUrl)

internal suspend fun HttpClient.clearHistoryMedia(
    mediaId: Int,
    baseUrl: String,
): HttpResponse = post(HistoryRequest.clearExactMedia(mediaId), baseUrl).also { response ->
    check(response.status.isSuccess()) {
        "History exact-media deletion failed with HTTP ${response.status.value}"
    }
}

private suspend fun HttpClient.get(
    request: HistoryRequest,
    baseUrl: String,
): HttpResponse = get(request.resolveUrl(baseUrl)) {
    apply(request)
}

private suspend fun HttpClient.post(
    request: HistoryRequest,
    baseUrl: String,
): HttpResponse = post(request.resolveUrl(baseUrl)) {
    apply(request)
}

private fun io.ktor.client.request.HttpRequestBuilder.apply(request: HistoryRequest) {
    request.query.forEach { (name, value) -> parameter(name, value) }
    headers {
        append(HttpHeaders.CacheControl, request.cacheControl)
    }
}
