# KinoPub API Client

Полный клиент для работы с API KinoPub сервиса на базе Ktor HTTP клиента для Android.

## Возможности

- 🔐 OAuth2 аутентификация через Device Flow
- 📱 Адаптирован для Android (OkHttp движок)
- 🔄 Автоматическое обновление токенов
- 📊 Полный набор API методов (контент, история, закладки, коллекции)
- 🛡️ Type-safe модели данных с Kotlinx Serialization
- ⚡ Корутины support из коробки
- 📝 HTTP логирование (опционально)
- 🆔 Динамический User-Agent на основе устройства и пользователя
- 🔧 Настраиваемые таймауты и retry политика

## HTTP Движок

Проект использует **OkHttp** как HTTP движок для Ktor клиента:

- **Стабильность**: Проверенный временем HTTP клиент
- **Производительность**: Эффективное управление соединениями
- **Функциональность**: Поддержка HTTP/2, connection pooling, interceptors
- **Таймауты**: Конфигурируемые таймауты на уровне OkHttp и Ktor
- **Автоматические параметры**: Interceptor автоматически добавляет CLIENT_ID и CLIENT_SECRET ко
  всем OAuth запросам

### Архитектура HTTP клиента

```kotlin
// 1. Создается OkHttp клиент с interceptors
val okHttpClient = createOkHttpClient(config) // Включает KinoPubParametersInterceptor

// 2. OkHttp передается в Ktor
val ktorClient = createHttpClient(config, okHttpClient, accessToken)

// 3. Interceptor автоматически добавляет параметры аутентификации
```

### KinoPubParametersInterceptor

Автоматически добавляет к OAuth запросам (`/oauth2/*`) как query параметры:

- `client_id` = "android"
- `client_secret` = из BuildConfig
- Добавляется в URL строку запроса
- Не дублирует уже существующие параметры

**Формат запроса:**

```
POST /oauth2/device?grant_type=device_code&client_id=android&client_secret=xxx&code=yyy&username=user
```

### Конфигурация таймаутов

```kotlin
KinoPubClientConfig(
    connectTimeout = 30_000,     // 30 секунд на установку соединения
    requestTimeout = 60_000,     // 60 секунд на весь запрос
    socketTimeout = 60_000,      // 60 секунд на чтение/запись
    retryOnFailure = true,       // Автоматические повторы при ошибках 5xx
    maxRetries = 3               // Максимум 3 попытки
)
```

## Изменения в архитектуре

### v2.0 - OkHttp Interceptor для автоматических параметров

**Что изменилось:**

- ✅ Создан `KinoPubParametersInterceptor` для автоматического добавления CLIENT_ID и CLIENT_SECRET
- ✅ OkHttp клиент создается отдельно и передается в Ktor через `createOkHttpClient()`
- ✅ Параметры аутентификации добавляются как query параметры в URL (соответствует референсной
  реализации)
- ✅ Убрано дублирование параметров аутентификации из кода

**Преимущества:**

- 🔒 **Безопасность**: Параметры аутентификации добавляются централизованно
- 🧹 **Чистота кода**: Убрано дублирование CLIENT_ID/CLIENT_SECRET в каждом запросе
- ⚙️ **Гибкость**: OkHttp можно настроить отдельно и переиспользовать
- 🔧 **Расширяемость**: Легко добавить новые interceptors
- ✅ **Совместимость**: Соответствует оригинальной реализации KinoPub API

**Обратная совместимость:** API клиента не изменился, все методы работают как раньше.

## User-Agent

Клиент автоматически генерирует User-Agent строку в формате:

```
kinopub/{versionName} device/{deviceModel} os/Android{androidVersion} username/{username}
```

Пример:

```
kinopub/1.0.0 device/SM-G950F os/Android14 username/myuser
```

## Быстрый старт

### 1. Создание клиента

```kotlin
import com.kino.puber.data.api.KinoPubClient

// Простое создание с логированием
val client = KinoPubClient.create(
    context = this, // Activity или Application context
    username = "myusername", // опционально
    enableLogging = true // Включает HTTP логи
)

// Создание с кастомной конфигурацией
val client = KinoPubClient.create(
    KinoPubClientConfig(
        context = applicationContext,
        username = "myusername",
        enableLogging = true,
        connectTimeout = 30_000,
        requestTimeout = 60_000
    )
)
```

### 2. Обновление username

```kotlin
// Обновить username после входа пользователя
client.updateUsername("newusername")
// User-Agent автоматически обновится для всех последующих запросов
```

### 3. Аутентификация

#### Device Flow (рекомендуется)

```kotlin
lifecycleScope.launch {
    val result = client.authenticateWithDeviceFlow("username")

    if (result.isSuccess) {
        val flowResult = result.getOrThrow()
        val deviceCode = flowResult.deviceCode

        // Показать пользователю код и URL для активации
        println("User Code: ${deviceCode.userCode}")
        println("Verification URL: ${deviceCode.verificationUri}")
    } else {
        println("Auth failed: ${result.exceptionOrNull()}")
    }
}
```

#### Использование существующих токенов

```kotlin
client.authenticateWithTokens(
    accessToken = "your_access_token",
    refreshToken = "your_refresh_token"
)
```

### 4. Использование API

#### Получение фильмов/сериалов

```kotlin
lifecycleScope.launch {
    // Получить список фильмов
    val moviesResult = client.getItems(
        type = "movie",
        sort = "created",
        page = 1
    )

    if (moviesResult.isSuccess) {
        val movies = moviesResult.getOrThrow()
        println("Found ${movies.items.size} movies")
        movies.items.forEach { movie ->
            println("${movie.title} (${movie.year})")
        }
    } else {
        println("Failed to fetch movies: ${moviesResult.exceptionOrNull()}")
    }
}
```

#### Поиск

```kotlin
lifecycleScope.launch {
    val searchResult = client.searchByTitle("Интерстеллар")

    if (searchResult.isSuccess) {
        val items = searchResult.getOrThrow()
        println("Search found ${items.items.size} results")
    } else {
        println("Search failed: ${searchResult.exceptionOrNull()}")
    }
}
```

#### Работа с историей просмотра

```kotlin
lifecycleScope.launch {
    // Получить историю
    val historyResult = client.getHistory("all")

    // Отметить время просмотра
    val markTimeResult = client.setWatchingTime(
        id = 12345,
        videoId = 67890,
        time = 1500 // секунды
    )

    // Добавить в список "К просмотру"
    val watchlistResult = client.toggleWatchlist(12345)
}
```

#### Закладки

```kotlin
lifecycleScope.launch {
    // Создать закладку
    val bookmarkResult = client.createBookmark("Мои фильмы")

    if (bookmarkResult.isSuccess) {
        val bookmark = bookmarkResult.getOrThrow()

        // Добавить фильм в закладку
        client.addBookmarkItem(
            itemId = 12345,
            folderId = bookmark.id
        )

        println("Bookmark created: ${bookmark.title}")
    }
}
```

#### Информация о пользователе

```kotlin
lifecycleScope.launch {
    val userInfoResult = client.getAccountInfo()

    if (userInfoResult.isSuccess) {
        val userInfo = userInfoResult.getOrThrow()
        println("User: ${userInfo.username}")
        println("Subscription active: ${userInfo.subscription?.active}")
    }
}
```

### 5. Обновление токенов

```kotlin
lifecycleScope.launch {
    val refreshResult = client.refreshAccessToken()

    if (refreshResult.isFailure) {
        // Токен невалиден, нужна повторная аутентификация
        client.clearAuthentication()
        // Запустить процесс аутентификации заново
    }
}
```

### 6. Закрытие клиента

```kotlin
override fun onDestroy() {
    super.onDestroy()
    client.close() // Освобождаем ресурсы
}
```

## Структура проекта

```
app/src/main/java/com/kino/puber/
├── PuberApplication.kt         # Application класс
├── data/api/
│   ├── auth/
│   │   ├── OAuthClient.kt           # OAuth аутентификация
│   │   └── OAuthModels.kt          # Модели аутентификации
│   ├── config/
│   │   ├── KinoPubConfig.kt        # Конфигурация и HTTP клиент
│   │   └── UserAgentBuilder.kt     # Построение User-Agent строки
│   ├── models/
│   │   └── Models.kt               # Модели данных API
│   ├── KinoPubApiClient.kt         # Низкоуровневый API клиент
│   └── KinoPubClient.kt            # Главный клиент (рекомендуется)
└── example/
    └── ApiUsageExample.kt          # Примеры использования
```

## Основные модели данных

- `Item` - Фильм/сериал/контент
- `Genre` - Жанр
- `Country` - Страна
- `History` - Запись в истории просмотра
- `Bookmark` - Закладка
- `KCollection` - Коллекция контента
- `UserInfo` - Информация о пользователе
- `DeviceSettings` - Настройки устройства

## Обработка ошибок

Все методы API возвращают `Result<T>` для безопасной обработки ошибок:

```kotlin
val result = client.getItems()

result.fold(
    onSuccess = { items ->
        // Успешный результат
        println("Got ${items.items.size} items")
    },
    onFailure = { error ->
        // Обработка ошибки
        println("API request failed: $error")
        when (error) {
            is HttpException -> println("HTTP Error: ${error.status}")
            is SerializationException -> println("Parse Error")
            else -> println("Unknown Error: ${error.message}")
        }
    }
)
```

## Зависимости

Клиент использует следующие библиотеки:

- Ktor (HTTP клиент)
- Kotlinx Serialization  (JSON парсинг)
- Kotlinx Coroutines (асинхронность)
- Kotlinx DateTime (работа с датами)

## Лицензия

Этот код является частью проекта Puber и распространяется в соответствии с лицензией проекта. 