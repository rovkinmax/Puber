# Recipe: API Endpoint Integration
> Related: [di-setup.md](di-setup.md)

How to add a new API endpoint.

## API client pattern

All endpoints live in `KinoPubApiClient`. No separate API interfaces per feature.

```kotlin
// In KinoPubApiClient
suspend fun getItems(type: String, page: Int = 1): Result<ItemsResponse> = apiCall {
    httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}v1/items") {
        parameter("type", type)
        parameter("page", page)
    }
}

suspend fun getItemDetails(id: Long): Result<ItemDetailsResponse> = apiCall {
    httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}v1/items/$id")
}
```

**Pattern:** `suspend fun` returning `Result<T>` via `apiCall { httpClient.get/post(...) }`

## Result handling

```kotlin
// In interactor
suspend fun getItems(): List<Item> {
    return apiClient.getItems(type = "movie").getOrThrow().items
}

// Or with explicit handling
suspend fun getItemSafe(): Item? {
    return apiClient.getItemDetails(id)
        .onSuccess { /* handle success */ }
        .onFailure { /* handle failure */ }
        .getOrNull()
        ?.item
}
```

## Interactor pattern

```kotlin
internal class MyInteractor(
    private val apiClient: KinoPubApiClient,
) {
    suspend fun getItems(type: String): List<Item> {
        return apiClient.getItems(type).getOrThrow().items
    }

    suspend fun getDetails(id: Long): Item {
        return apiClient.getItemDetails(id).getOrThrow().item
    }
}
```

**No `@InjectConstructor`** — Koin resolves via `scopedOf(::MyInteractor)` in `buildModule`.

## Interface pattern (for global interactors)

```kotlin
// Interface
interface IAuthInteractor {
    suspend fun login(code: String): Result<TokenResponse>
    suspend fun refreshToken(): Result<TokenResponse>
}

// Implementation
class AuthInteractor(
    private val apiClient: KinoPubApiClient,
    private val cryptoRepo: ICryptoPreferenceRepository,
) : IAuthInteractor {
    override suspend fun login(code: String) = apiClient.getToken(code)
    override suspend fun refreshToken() = apiClient.refreshToken(savedRefreshToken)
}

// DI registration (global)
singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
```

Screen-scoped interactors typically don't need an interface.

## API models

Models are `@Serializable` data classes in `data/api/models/`. Used directly — no separate domain entity layer:

```kotlin
@Serializable
data class Item(
    val id: Long,
    val title: String,
    val type: String,
    val posters: Posters? = null,
    val imdb_rating: Double? = null,
    val kinopoisk_rating: Double? = null,
    // ...
)
```

## DI registration

```kotlin
// Screen-scoped interactor
private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
    scope(named(scopeId)) {
        scopedOf(::MyInteractor)
        scopedOf(::MyUIMapper)
        viewModelOf(::MyVM)
    }
}

// Global interactor (in PuberApp modules)
val interactorModule = module {
    singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
}
```

## Caching with TypedTtlCache

```kotlin
internal class MyInteractor(
    private val apiClient: KinoPubApiClient,
    private val cache: TypedTtlCache<Long, Item>,
) {
    suspend fun getDetails(id: Long): Item {
        return cache.getOrPut(id) {
            apiClient.getItemDetails(id).getOrThrow().item
        }
    }
}
```

## Checklist
- [ ] Endpoint added to `KinoPubApiClient` (not a separate API interface)
- [ ] Returns `Result<T>` via `apiCall { ... }`
- [ ] Base URL: `KinoPubConfig.MAIN_API_BASE_URL` or `KinoPubConfig.OAUTH_BASE_URL`
- [ ] Interactor calls `apiClient` methods, handles `Result` with `.getOrThrow()` or `.onSuccess/.onFailure`
- [ ] No `@InjectConstructor` — pure Koin DSL
- [ ] No Retrofit, no separate API interfaces per feature
- [ ] Uses `@Serializable` data classes from `data/api/models/`
- [ ] Error handling in VM (`dispatchError` or `launch` with `DefaultExceptionHandler`)
