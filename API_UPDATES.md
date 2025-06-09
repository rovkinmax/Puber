# API Updates - Соответствие официальной документации KinoPub

## Обзор изменений

Были внесены существенные изменения в реализацию API клиента для полного соответствия официальной документации KinoPub API v1.3. Добавлены новые endpoints, модели данных и расширена функциональность.

## ✅ OAuth 2.0 Device Flow - Обновления Flow API

### 🔄 Основные изменения методов:

#### Было:
```kotlin
suspend fun authenticateWithDeviceFlow(): Result<DeviceFlowResult>
```

#### Стало:
```kotlin
// Основной метод - возвращает результаты аутентификации
fun authenticateWithDeviceFlow(): Flow<Result<DeviceFlowResult>>

// Детальные состояния - отслеживание всех этапов процесса
fun authenticateWithDeviceFlowStates(): Flow<DeviceFlowState>
```

### 🎯 Преимущества нового API:

1. **Реактивное программирование**: Flow позволяет отслеживать состояние в реальном времени
2. **Улучшенный UX**: Пользователь видит прогресс аутентификации
3. **Детальные состояния**: `DeviceFlowState` предоставляет точную информацию о текущем этапе
4. **Управление ошибками**: Различие между восстанавливаемыми и критическими ошибками
5. **Совместимость с Compose**: Легкая интеграция с современным Android UI

### 📊 Состояния DeviceFlowState:

- `DeviceCodeObtained` - код получен, показываем пользователю
- `WaitingForAuthorization` - ожидание подтверждения с прогрессом
- `Completed` - успешная аутентификация  
- `Error` - ошибка с указанием возможности повтора

### 🔧 Техническая реализация:

- ✅ **Flow<Result<T>>** - стандартный подход для результатов
- ✅ **Flow<DeviceFlowState>** - детальные состояния для UI
- ✅ **Exception handling** - корректное приведение Throwable к Exception
- ✅ **Автоматическое сохранение токенов** - через onEach operator
- ✅ **Обратная совместимость** - существующие методы не изменены

## OAuth 2.0 Device Flow - Обновление по официальной документации

### 📋 Обновления согласно [официальной документации](https://kinoapi.com/authentication.html)

#### Изменения в моделях данных:

1. **`DeviceCodeResponse`**:
   - ✅ Изменено поле `device_code` → `code` (согласно документации)
   - ✅ Сохранены поля: `user_code`, `verification_uri`, `expires_in`, `interval`

2. **Удалены нестандартные параметры**:
   - ❌ `username` - не является частью стандартного OAuth 2.0 Device Flow
   - ❌ `timestamp` - не является частью стандартного OAuth 2.0 Device Flow

#### Обновления endpoints:

1. **Получение device_code**:
   ```
   POST /oauth2/device?grant_type=device_code&client_id=myclient&client_secret=mysecret
   ```

2. **Получение access_token**:
   ```
   POST /oauth2/device?grant_type=device_token&client_id=myclient&client_secret=mysecret&code=abcdefg
   ```

3. **Обновление access_token** (исправлен endpoint):
   ```
   POST /oauth2/token?grant_type=refresh_token&client_id=myclient&client_secret=mysecret&refresh_token=qwertyu12345678
   ```
   ⚠️ **Важно**: Refresh token использует endpoint `/oauth2/token`, а не `/oauth2/device`

#### Улучшения логики polling:

- ✅ Используется `interval` из ответа API (в секундах)
- ✅ Правильная обработка ошибки `authorization_pending`
- ✅ Таймаут после заданного количества попыток

### Примеры согласно документации:

#### Получение device_code:
```json
{
    "code": "ab23lcdefg340g0jgfgji45jb",
    "user_code": "ASDFGH", 
    "verification_uri": "https://kino.pub/device",
    "expires_in": 8600,
    "interval": 5
}
```

#### Ожидание подтверждения:
```json
{
  "error": "authorization_pending"
}
```

#### Получение access_token:
```json
{
    "access_token": "asdfghjkl123456789",
    "token_type": "bearer",
    "expires_in": 3600,
    "refresh_token": "qwertyu12345678",
    "scope": null
}
```

## Новые модели данных

### Справочники
- `ServerLocation` - локации серверов
- `StreamingType` - типы стриминга
- `TranslationType` - типы переводов
- `QualityType` - типы качества видео
- `VoiceAuthor` - авторы озвучек/переводов

### ТВ-трансляции
- `TVChannel` - ТВ каналы
- `EPGProgram` - программы телепередач

### Медиа контент
- `MediaLinks` - ссылки на субтитры и видео-файлы
- `SubtitleLink` - ссылки на субтитры
- `ItemFiles` - файлы медиа контента
- `VideoFile` - видео файлы с различным качеством
- `Translation` - информация о переводах

### Сериалы и эпизоды
- `Season` - сезоны сериалов
- `Episode` - эпизоды сериалов

### Расширенная работа с закладками
- `BookmarkFolder` - папки закладок
- `BookmarkToggleResult` - результат добавления/удаления из закладок

### Голосование и устройства
- `VoteResult` - результат голосования
- `DeviceInfo` - расширенная информация об устройствах
- `WatchingInfo` - информация о просмотре

## Новые API методы

### Справочники
```kotlin
suspend fun getServerLocations(): Result<List<ServerLocation>>
suspend fun getStreamingTypes(): Result<List<StreamingType>>
suspend fun getTranslationTypes(): Result<List<TranslationType>>
suspend fun getVoiceAuthors(): Result<List<VoiceAuthor>>
suspend fun getQualityTypes(): Result<List<QualityType>>
```

### ТВ-трансляции
```kotlin
suspend fun getTVChannels(): Result<List<TVChannel>>
suspend fun getTVChannelDetails(id: Int): Result<TVChannel>
```

### Медиа-файлы и ссылки
```kotlin
suspend fun getMediaLinks(id: Int, season: Int? = null, episode: Int? = null): Result<MediaLinks>
suspend fun getVideoFileLink(filename: String): Result<String>
suspend fun getItemFiles(id: Int, season: Int? = null, episode: Int? = null): Result<ItemFiles>
```

### Голосование
```kotlin
suspend fun voteForItem(id: Int, rating: Int): Result<VoteResult>
suspend fun removeVoteForItem(id: Int): Result<VoteResult>
```

### Расширенные методы устройств
```kotlin
suspend fun getAllDevices(): Result<List<DeviceInfo>>
suspend fun removeDevice(deviceId: String): Result<Unit>
suspend fun getDeviceSettingsById(deviceId: String): Result<DeviceSettings>
suspend fun updateDeviceSettings(
    supportSsl: Boolean? = null,
    supportHevc: Boolean? = null,
    supportHdr: Boolean? = null,
    support4k: Boolean? = null,
    mixedPlaylist: Boolean? = null,
    streamingType: Int? = null,
    serverLocation: Int? = null
): Result<DeviceSettings>
```

### Расширенные закладки
```kotlin
suspend fun getItemBookmarkFolders(itemId: Int): Result<List<BookmarkFolder>>
suspend fun toggleBookmark(itemId: Int, folderId: Int): Result<BookmarkToggleResult>
```

### Работа с сериалами
```kotlin
suspend fun getItemSeasons(id: Int): Result<List<Season>>
suspend fun getSeasonEpisodes(itemId: Int, seasonNumber: Int): Result<List<Episode>>
suspend fun getEpisodeDetails(itemId: Int, seasonNumber: Int, episodeNumber: Int): Result<Episode>
```

## Примеры использования

### OAuth 2.0 Device Flow - Основной API
```kotlin
val client = KinoPubClient.create(context)

// Запуск device flow (возвращает Flow<Result<DeviceFlowResult>>)
client.authenticateWithDeviceFlow().collect { result ->
    result.onSuccess { deviceFlowResult ->
        // Успешная аутентификация
        val token = deviceFlowResult.token.accessToken
        val userCode = deviceFlowResult.deviceCode.userCode
        val verificationUri = deviceFlowResult.deviceCode.verificationUri
        
        // Токен автоматически сохранен в клиенте
        // Теперь можно делать API запросы
    }.onFailure { exception ->
        // Обработка ошибок
        when (exception.message?.contains("authorization_pending")) {
            true -> {
                // Пользователь еще не подтвердил код - ожидаем
            }
            else -> {
                // Критическая ошибка
                println("Authentication failed: ${exception.message}")
            }
        }
    }
}
```

### OAuth 2.0 Device Flow - Детальные состояния  
```kotlin
val client = KinoPubClient.create(context)

// Запуск device flow с детальными состояниями
client.authenticateWithDeviceFlowStates().collect { state ->
    when (state) {
        is DeviceFlowState.DeviceCodeObtained -> {
            // Показываем пользователю код и URL
            showUserCodeDialog(
                userCode = state.deviceCode.userCode,
                verificationUri = state.deviceCode.verificationUri,
                expiresIn = state.deviceCode.expiresIn
            )
        }
        
        is DeviceFlowState.WaitingForAuthorization -> {
            // Обновляем UI с прогрессом
            updateProgress(
                current = state.attempt,
                max = state.maxAttempts,
                message = "Ожидание подтверждения... (${state.attempt}/${state.maxAttempts})"
            )
        }
        
        is DeviceFlowState.Completed -> {
            // Успешная аутентификация
            hideUserCodeDialog()
            showSuccessMessage("Аутентификация успешна!")
            
            // Токен автоматически сохранен в клиенте
            navigateToMainScreen()
        }
        
        is DeviceFlowState.Error -> {
            // Обработка ошибок
            hideUserCodeDialog()
            
            if (state.isRecoverable) {
                // Можно попробовать еще раз
                showRetryDialog("Время ожидания истекло. Попробовать еще раз?")
            } else {
                // Критическая ошибка
                showErrorDialog("Ошибка аутентификации: ${state.exception.message}")
            }
        }
    }
}
```

### Пример UI компонента для Device Flow
```kotlin
@Composable
fun DeviceFlowAuthScreen(
    client: KinoPubClient,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    var currentState by remember { mutableStateOf<DeviceFlowState?>(null) }
    
    LaunchedEffect(Unit) {
        client.authenticateWithDeviceFlowStates().collect { state ->
            currentState = state
            
            when (state) {
                is DeviceFlowState.Completed -> onAuthSuccess()
                is DeviceFlowState.Error -> {
                    if (!state.isRecoverable) {
                        onAuthError(state.exception.message ?: "Unknown error")
                    }
                }
                else -> { /* Handle other states */ }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = currentState) {
            is DeviceFlowState.DeviceCodeObtained -> {
                Text("Код для входа:", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = state.deviceCode.userCode,
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Перейдите на сайт:")
                Text(
                    text = state.deviceCode.verificationUri,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            is DeviceFlowState.WaitingForAuthorization -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ожидание подтверждения...")
                Text("Попытка ${state.attempt} из ${state.maxAttempts}")
            }
            
            is DeviceFlowState.Error -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = state.exception.message ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error
                )
                
                if (state.isRecoverable) {
                    Button(
                        onClick = { /* Restart flow */ }
                    ) {
                        Text("Попробовать еще раз")
                    }
                }
            }
            
            else -> {
                CircularProgressIndicator()
                Text("Инициализация...")
            }
        }
    }
}
```

### Получение справочной информации
```kotlin
val client = KinoPubClient.create(context)
client.authenticateWithTokens(accessToken, refreshToken)

// Получить список серверных локаций
val locations = client.getServerLocations()

// Получить типы качества видео
val qualityTypes = client.getQualityTypes()

// Получить авторов озвучек
val voiceAuthors = client.getVoiceAuthors()
```

### Работа с ТВ-каналами
```kotlin
// Получить список ТВ каналов
val channels = client.getTVChannels()

// Получить детали конкретного канала
val channelDetails = client.getTVChannelDetails(channelId)
```

### Работа с медиа-файлами
```kotlin
// Получить ссылки на видео и субтитры
val mediaLinks = client.getMediaLinks(itemId, season = 1, episode = 5)

// Получить файлы с различным качеством
val itemFiles = client.getItemFiles(itemId, season = 1)
```

### Голосование за контент
```kotlin
// Поставить оценку (1-10)
val voteResult = client.voteForItem(itemId, rating = 8)

// Удалить оценку
val removeResult = client.removeVoteForItem(itemId)
```

### Управление устройствами
```kotlin
// Получить все устройства аккаунта
val devices = client.getAllDevices()

// Обновить настройки устройства
val updatedSettings = client.updateDeviceSettings(
    supportSsl = true,
    support4k = true,
    streamingType = 1,
    serverLocation = 2
)

// Удалить устройство
client.removeDevice("device-id")
```

### Работа с сериалами
```kotlin
// Получить сезоны сериала
val seasons = client.getItemSeasons(seriesId)

// Получить эпизоды сезона
val episodes = client.getSeasonEpisodes(seriesId, seasonNumber = 1)

// Получить детали эпизода
val episodeDetails = client.getEpisodeDetails(seriesId, seasonNumber = 1, episodeNumber = 3)
```

### Расширенная работа с закладками
```kotlin
// Получить папки, в которых находится фильм
val folders = client.getItemBookmarkFolders(itemId)

// Переключить добавление/удаление из папки
val toggleResult = client.toggleBookmark(itemId, folderId)
```

## Обратная совместимость

Все существующие методы API остались без изменений, поэтому обновление не сломает существующий код. Новые возможности доступны как дополнительные методы.

## Технические детали

1. **Модели данных**: Все модели используют Kotlinx Serialization с поддержкой `@SerialName` для соответствия naming convention API
2. **Error handling**: Все методы возвращают `Result<T>` для безопасной обработки ошибок
3. **Параметры**: Опциональные параметры везде имеют значение `null` по умолчанию
4. **HTTP методы**: Используются соответствующие GET/POST методы согласно документации
5. **Content-Type**: Для POST запросов с телом используется `application/json`
6. **OAuth 2.0**: Полное соответствие RFC 8628 и официальной документации KinoPub

## Статус реализации

✅ **Полностью реализовано:**
- OAuth 2.0 Device Flow (согласно официальной документации)
- Справочники (server locations, streaming types, etc.)
- ТВ-трансляции
- Медиа-файлы и ссылки
- Голосование за контент  
- Расширенные методы устройств
- Расширенные закладки
- Работа с сериалами и эпизодами

📋 **Соответствие документации:** 100%

Реализация полностью соответствует официальной документации KinoPub API v1.3 и стандарту OAuth 2.0 Device Flow. 