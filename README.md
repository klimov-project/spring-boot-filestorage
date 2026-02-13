# Cloud File Storage — backend (текущий статус)

Описание: Бэкенд для облачного файлового хранилища (2 этап — реализована часть функционала работы с файлами). Фронтенд тестовый.

Требования:

- Java, Gradle
- Spring Boot, Spring Security
- Spring Session (Redis)
- JPA (Postgres/MySQL)
- MinIO (S3-совместимое хранилище)
- Docker для Postgres/Redis/MinIO

### Запуск (локально, dev):

1. Поднять зависимости (Postgres, Redis, MinIO) через Docker Compose (файлы `docker-compose*.yml` присутствуют в корне).

```bash
docker-compose up -d --build postgres redis minio
```

2. Перейти в директорию с backend и запустить:

```bash
cd backend-gradle-initializr
./gradlew bootRun
```

3. Перейти в директорию с frontend и запустить:

```bash
cd frontend-placeholder
npm run dev
```

### Запуск всей сборки в докере:

```bash
docker-compose up -d --build
```

## Основные достижения и архитектурные решения текущего этапа

### 1. Изоляция пользователей

Каждый пользователь имеет свою изолированную файловую структуру в MinIO:

- Формат: `user-{id}-files/`
- Автоматическое создание корневой папки при регистрации

### 2. Паттерн обработки ошибок с Adapter Pattern

Реализована чистая архитектура для преобразования ошибок:

#### Архитектура обработки ошибок:

```
Controller  →  StorageService  →  MinioServiceAdapter  →  MinioServiceImpl
      ↑                                ↑                         ↑
      └─── возвращает                  └─── преобразует          └─── выбрасывает
      StorageException              fullPath → relativePath         RuntimeException
```

#### Ключевые компоненты:

- **`MinioServiceAdapter`** - преобразует относительные пути в полные и обратно, трансформирует исключения
- **`StorageException`** - единая точка для всех ошибок хранилища с относительными путями
- **`GlobalExceptionHandler`** - централизованная обработка всех исключений API

### 3. Статусы ошибок

- **Нет утечки внутренних путей** - пользователи видят только относительные пути (`test-folder/` вместо `user-3-files/test-folder/`)
- **Правильные HTTP-статусы**:
  - `400 Bad Request` - невалидные данные
  - `401 Unauthorized` - ошибки аутентификации
  - `403 Forbidden` - доступ запрещен
  - `404 Not Found` - ресурс не существует
  - `409 Conflict` - ресурс уже существует
  - `500 Internal Server Error` - внутренние ошибки
- **Консистентный формат ответов**: `{"message": "текст ошибки"}`

### 4. Исключения (структура)

```
exception/
├── GlobalExceptionHandler.java         # Обработчик всех исключений приложения
├── StorageException.java               # Исключения хранилища (с относительными путями)
│   ├── InvalidPathException            # 400 - невалидный путь
│   ├── ResourceNotFoundException       # 404 - ресурс не найден
│   ├── ResourceAlreadyExistsException  # 409 - ресурс уже существует
│   └── StorageOperationException       # 500 - ошибка операции
└── UsernameExistsException.java        # 409 - пользователь уже существует
```

### 5. Особенности тестирования

#### Работающие проверки в тестах:

- Регистрация пользователя (201 Created)
- Вход с правильными данными (200 OK)
- Вход с неправильными данными (401 Unauthorized)
- Дублирование пользователей (409 Conflict)
- Валидация данных (400 Bad Request)
- Хеширование паролей в БД

## Инструкция по запуску тестов

### 1. Подготовка окружения

```bash
# Запуск зависимостей в корневой папке проекта
docker-compose up -d postgres redis minio
```

### 2. Запуск тестов

```bash
# Переход в директорию с приложением
cd backend-gradle-initializr

# Очистка и запуск тестов
./gradlew clean test

# Или для конкретного тестового класса
./gradlew test --tests "com.project.AuthControllerIntegrationTest"
```

### 3. Ключевые тестовые сценарии

```java
// Основные проверяемые сценарии:
signUp_and_signIn_success()              // Полный цикл регистрации/входа
signUp_duplicateUsername_returns409()    // Конфликт пользователей
signUp_invalidData_returnsBadRequest()   // Валидация данных
signIn_wrongPassword_returns401()        // Неверный пароль
```

### 4. Важные моменты

1. **HTTP статусы при работе с файлами** - контролируем в [StorageException.java](backend-gradle-initializr/src/main/java/com/project/exception/StorageException.java).
2. **Тесты не проверяют Set-Cookie** - это нормально, сессии отключены в тестовой среде. _Рекомендация: настроить дополнительный конфиг чтобы фильтры Spring Security отработывали в mockMvc._
3. **Все операции через MinioServiceAdapter** - здесь гарантируем сокрытие инфы об устройстве внутренних сервисов: исключения выбрасываются как `StorageException` - с относительными путями, которые доступны на клиенте

### 5. Команды для отладки

```bash
# Запуск с логированием
./gradlew test --info

# Запуск одного теста
./gradlew test --tests "*signUp_and_signIn_success"

# Пересборка и запуск
./gradlew clean build test

# Просмотр отладочного вывода
./gradlew test --debug
```

### 6. Что тестируется успешно

- [x] Регистрация пользователей
- [x] Аутентификация (без сессий)
- [x] Валидация данных
- [x] Конфликты пользователей
- [x] Хеширование паролей
- [x] Изоляция данных (через адаптер путей)
- [x] Правильные HTTP статусы ошибок

### 7. Опционально

- [ ] Интеграционные тесты сервиса по работе с файлами
  - [x] Создание папки
  - [ ] Другие важные кейсы

## Предыдущий статус (этап 1)

Важные файлы для проверки:

- Контроллеры: [AuthController.java](backend-gradle-initializr/src/main/java/com/project/controller/AuthController.java#L1-L200)
- Сервисы: [AuthService.java](backend-gradle-initializr/src/main/java/com/project/service/AuthService.java#L1-L200)
- Конфигурация безопасности: [SecurityConfig.java](backend-gradle-initializr/src/main/java/com/project/config/SecurityConfig.java#L1-L200)
- Обработчик исключений: [GlobalExceptionHandler.java](backend-gradle-initializr/src/main/java/com/project/exception/GlobalExceptionHandler.java#L1-L200)

API (реализовано сейчас):

- POST `/api/auth/sign-up` — регистрация. Успех: `201 Created` + `{ "username": "..." }`
- POST `/api/auth/sign-in` — вход. Ожидается `200 OK` + `{ "username": "..." }`
- POST `/api/auth/sign-out` — выход. Ожидается `204 No Content` (обрабатывается Spring Security).
- POST `/api/user/me` — текущий пользователь. Ожидается `200 OK` + `{ "username": "..." }`

Формат ошибок: сервис использует `{ "message": "Текст ошибки" }` — покрыто в `GlobalExceptionHandler` для валидации и общих ошибок; отдельные обработчики возвращают 401/409.

Что сделано:

- Регистрация пользователей
- Проверка уникальности логина (типизированное исключение `UsernameExistsException`)
- Шифрование пароля (BCrypt)
- Глобальная обработка ошибок валидации
- Обработчик `AuthenticationException` → 401

Что нужно сделать дальше (очередность):

1. Добавить unit/integration тесты для регистрации/логина/логаута (в проект добавлены базовые интеграционные тесты для auth).
2. ̶П̶о̶к̶р̶ы̶т̶ь̶ ̶т̶е̶с̶т̶а̶м̶и̶ ̶с̶ц̶е̶н̶а̶р̶и̶и̶ ̶о̶ш̶и̶б̶о̶к̶ ̶и̶ ̶с̶о̶з̶д̶а̶н̶и̶е̶ ̶с̶е̶с̶с̶и̶и̶ ̶(̶c̶o̶o̶k̶i̶e̶)̶.̶
3. (Опционально) Проверить и расширить обработчики 404/409 в `GlobalExceptionHandler` при необходимости.

Примечания:

- Logout реализован через Spring Security (`/api/auth/sign-out` настроен в `SecurityConfig`).
