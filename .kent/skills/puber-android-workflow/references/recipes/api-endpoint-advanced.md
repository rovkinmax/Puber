# Recipe: API Endpoint -- Advanced Patterns
> Load this recipe additionally when the step involves: caching, flow-based polling, multiple API calls, TypedTtlCache.
> Core patterns are in [api-endpoint.md](api-endpoint.md).

## Caching with AtomicReference (simple)

For shared data across screens, cache at interactor level:

```kotlin
internal class MyInteractor(private val apiClient: KinoPubApiClient) {

    private val cachedData = AtomicReference<MyData?>(null)

    suspend fun getData(): Result<MyData> {
        cachedData.get()?.let { return Result.success(it) }
        return fetchAndCache()
    }

    private suspend fun fetchAndCache(): Result<MyData> {
        return apiClient.getData().also { result ->
            result.onSuccess { cachedData.set(it) }
        }
    }

    fun getCachedData(): MyData? = cachedData.get()

    fun clearCache() { cachedData.set(null) }
}
```

## Caching with TypedTtlCache (TTL-based, per-key)

For per-key caching with expiry (e.g., item details):

```kotlin
internal class DetailsInteractor(
    private val apiClient: KinoPubApiClient,
    private val cache: TypedTtlCache<Int, Item>,
) {
    suspend fun getItemDetails(itemId: Int): Result<Item> {
        cache.get(itemId)?.let { return Result.success(it) }
        return apiClient.getItemDetails(itemId).also { result ->
            result.onSuccess { cache.put(itemId, it) }
        }
    }

    fun invalidate(itemId: Int) {
        cache.remove(itemId)
    }
}
```

DI registration:
```kotlin
// In interactorModule (global)
single { TypedTtlCacheImpl<Int, Item>(ttlMs = 5 * 60 * 1000L) } // 5 min TTL
singleOf(::DetailsInteractor)

// Or in screen scope
scope(named(scopeId)) {
    scoped { TypedTtlCacheImpl<Int, Item>(ttlMs = 5 * 60 * 1000L) }
    scopedOf(::DetailsInteractor)
}
```

## Thread-safe collections (for list VMs)

```kotlin
// In PagingVM
private val cachedItems = Collections.synchronizedList(mutableListOf<Item>())

// On first page load
cachedItems.clear()
cachedItems.addAll(result.items)

// On next page
cachedItems.addAll(result.items)

// On item update
val index = cachedItems.indexOfFirst { it.id == updated.id }
if (index >= 0) cachedItems[index] = updated
```

## Multiple API calls in one interactor

Interactor may combine several `KinoPubApiClient` calls:

```kotlin
internal class BrowseInteractor(private val apiClient: KinoPubApiClient) {

    suspend fun getHomeData(): Result<HomeData> {
        return runCatching {
            coroutineScope {
                val freshDeferred = async { apiClient.getFreshItems().getOrThrow() }
                val hotDeferred = async { apiClient.getHotItems().getOrThrow() }
                val bookmarksDeferred = async { apiClient.getBookmarks().getOrThrow() }

                HomeData(
                    fresh = freshDeferred.await(),
                    hot = hotDeferred.await(),
                    bookmarks = bookmarksDeferred.await(),
                )
            }
        }
    }
}
```

## Flow-based endpoint (polling pattern)

For device auth or similar polling flows:

```kotlin
internal class DeviceAuthInteractor(private val apiClient: KinoPubApiClient) {

    fun pollDeviceAuth(code: String, interval: Long): Flow<Result<AuthToken>> = flow {
        while (true) {
            val result = apiClient.checkDeviceCode(code)
            emit(result)
            if (result.isSuccess) break
            delay(interval * 1000L)
        }
    }
}

// In VM
private fun startPolling(code: String, interval: Long) {
    pollingJob?.cancel()
    pollingJob = launch {
        interactor.pollDeviceAuth(code, interval)
            .collect { result ->
                result.onSuccess { token ->
                    authInteractor.saveToken(token)
                    router.newRootScreen(Screens.main())
                }.onFailure { error ->
                    // Check if it's "authorization_pending" -- keep polling
                    // Otherwise show error
                    if (error !is AuthPendingException) {
                        updateViewState(AuthViewState.Error(error.message.orEmpty()))
                    }
                }
            }
    }
}

override fun onCleared() {
    pollingJob?.cancel()
    super.onCleared()
}
```

## KinoPubApiClient patterns

All endpoints are `suspend fun` returning `Result<T>`:

```kotlin
// In KinoPubApiClient
suspend fun getItems(
    type: String? = null,
    page: Int = 1,
    perPage: Int = 20,
): Result<ItemsResponse> = apiCall {
    httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}/items") {
        parameter("type", type)
        parameter("page", page)
        parameter("perPage", perPage)
    }
}

suspend fun getItemDetails(id: Int): Result<ItemDetailsResponse> = apiCall {
    httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}/items/$id")
}

suspend fun toggleFavorite(id: Int, isFavorite: Boolean): Result<Unit> = apiCall {
    if (isFavorite) {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}/bookmarks/add") {
            parameter("item", id)
        }
    } else {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}/bookmarks/remove") {
            parameter("item", id)
        }
    }
}
```

## Result<T> handling patterns

```kotlin
// Simple success/failure
result.onSuccess { data -> updateViewState(mapper.mapToContent(data)) }
      .onFailure { error -> dispatchError(ErrorEntity(error.message.orEmpty())) }

// Map result
val items = result.getOrDefault(emptyList())

// Chain results
val details = apiClient.getItemDetails(id).getOrThrow()
val seasons = apiClient.getSeasons(id).getOrDefault(emptyList())

// In coroutineScope with multiple calls
coroutineScope {
    val a = async { apiClient.getA().getOrThrow() }
    val b = async { apiClient.getB().getOrThrow() }
    // If any throws, coroutineScope propagates the exception
    mapToState(a.await(), b.await())
}
```

## Serialization notes

- Global `Json` config: `explicitNulls = false`, `encodeDefaults = false`
- Models use `@Serializable` from `kotlinx.serialization`
- API models are in `com.kino.puber.data.api.models`
- No separate domain entity layer -- API models used directly
- No Retrofit, no generated SDK -- all in `KinoPubApiClient`

## Anti-patterns
- **Never create separate API interfaces** -- all endpoints go in `KinoPubApiClient`
- **Never wrap Result in another Result** -- pass through, don't double-wrap
- **Never block the main thread** -- all API calls are `suspend`, use `launch { }`
- **Never cache without TTL** -- use `TypedTtlCache` or `AtomicReference` with manual invalidation
- **Never use `runBlocking`** -- always `runTest` in tests, `launch` in VM

## Checklist
- [ ] All API calls return `Result<T>` via `apiCall { }`
- [ ] Caching uses `TypedTtlCache` (per-key with TTL) or `AtomicReference` (simple)
- [ ] Parallel calls via `coroutineScope { async { } }`
- [ ] Polling uses `Flow` with `delay()` loop
- [ ] `getOrThrow()` inside `coroutineScope` for parallel error propagation
- [ ] `onSuccess`/`onFailure` for single-call result handling
- [ ] No separate domain entities -- use API models directly
- [ ] Cache registered in Koin (`single` or `scoped` depending on lifecycle)
- [ ] `clearCache()` / `invalidate()` methods for manual cache busting
