# Cloud File Storage — backend (текущий статус)

Описание: Бэкенд для облачного файлового хранилища (1 этап — реализованы регистрация, авторизация, выход из аккаунта; без работы с файлами). Фронтенд тестовый.

Требования:

- Java, Gradle
- Spring Boot, Spring Security
- Spring Session (Redis)
- JPA (Postgres/MySQL)
- MinIO (S3-совместимое хранилище)
- Docker для Postgres/Redis/MinIO

Запуск (локально, dev):

1. Поднять зависимости (Postgres, Redis, MinIO) через Docker Compose (файлы `docker-compose*.yml` присутствуют в корне).

```bash
docker-compose up -d --build postgres redis minio
```

2. Запустить приложение:

```bash
./gradlew bootRun
```

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
2. Покрыть тестами сценарии ошибок и создание сессии (cookie).
3. (Опционально) Проверить и расширить обработчики 404/409 в `GlobalExceptionHandler` при необходимости.

Тесты:

- Интеграционные тесты для `AuthController` находятся в `src/test/java/com/project/AuthControllerIntegrationTest.java`.

Примечания:

- Logout реализован через Spring Security (`/api/auth/sign-out` настроен в `SecurityConfig`).
