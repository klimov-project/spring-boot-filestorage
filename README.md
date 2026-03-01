# Cloud File Storage — backend (текущий статус)

Бэкенд для облачного файлового хранилища (3 этап — завершена работа с файлами и папками). Фронтенд тестовый.

**Технологический стек**

![Java](https://img.shields.io/badge/java-black?style=for-the-badge&logo=java&link=https%3A%2F%2Fwww.java.com%2Fen%2F)
![Gradle](https://img.shields.io/badge/gradle-black?style=for-the-badge&logo=gradle&link=https%3A%2F%2Fgradle.org)
![Spring Boot](https://img.shields.io/badge/Spring%20boot-black?style=for-the-badge&logo=spring%20boot&link=https%3A%2F%2Fspring.io)
![Redis](https://img.shields.io/badge/redis-black?style=for-the-badge&logo=redis&link=https%3A%2F%2Fredis.io)
![Minio](https://img.shields.io/badge/minio-black?style=for-the-badge&logo=minio&link=https%3A%2F%2Fmin.io)
![Docker](https://img.shields.io/badge/docker-black?style=for-the-badge&logo=docker&link=https%3A%2F%2Fwww.docker.com)

- Java, Gradle
- Spring Boot, Spring Security
- Spring Session (Redis)
- JPA (Postgres/MySQL)
- MinIO (S3-совместимое хранилище)
- Docker для Postgres/Redis/MinIO

## Запуск

**Локально (dev):**

```bash
# Поднять зависимости (корень проекта)
docker-compose up -d postgres redis minio

# Бэкенд
cd backend-gradle-initializr && ./gradlew bootRun

# Фронтенд (отдельно)
cd frontend-placeholder && npm run dev
```

## 🚀 Быстрый старт с Docker

1. **Клонировать репозиторий:**

   ```bash
   git clone https://github.com/klimov-project/spring-boot-filestorage.git
   cd spring-boot-filestorage
   ```

2. **Настроить переменные окружения:**

   ```bash
   cp .env.example .env && cp ./frontend-placeholder/.env.example ./frontend-placeholder/.env && cp docker-compose.prod.yml docker-compose.yml
   ```

3. **Запустить сборку и контейнеры:**

   ```bash
   docker-compose up -d --build
   ```

   Приложение будет доступно по адресу: `http://ваш-сервер`

## Ключевые достижения текущего этапа

### Архитектура и обработка ошибок

- **Изоляция пользователей**: `user-{id}-files/` в MinIO, корневая папка создаётся при регистрации
- **Adapter Pattern**: трансляция относительных путей ↔ полных, преобразование исключений
- **Единый формат ошибок**: `{"message": "текст ошибки"}` с корректными HTTP-статусами
- **Структура исключений**:

```txt

StorageException (базовое)
├── InvalidPathException # 400
├── ResourceNotFoundException # 404
├── ResourceAlreadyExistsException # 409
└── StorageOperationException # 500

```

### Что сделано

- ✅ Полный цикл CRUD для папок и файлов
- ✅ Валидация путей (единый подход, без ведущего слеша)
- ✅ Обработка "Object not Exist" → 404
- ✅ Рефакторинг тестов: замена `uploadToNonexistentPath` на `directoryInfoForNonexistFolder`

### Тестирование: базовый функционал регистрации пользователей

- [x] Регистрация пользователей
- [x] Аутентификация (без сессий)
- [x] Валидация данных
- [x] Конфликты пользователей
- [x] Хеширование паролей
- [x] Изоляция данных (через адаптер путей)
- [x] Правильные HTTP статусы ошибок

### Дополнительно: покрытие функционала файлового хранилища

- [x] Создание директории
- [x] Получение содержимого директории
- [x] Ошибка "директория не найдена"
- [x] Ошибка "некорректный путь"
- [x] Удаление файла
- [x] Удаление директории
- [x] Удаление несуществующего объекта
- [x] Скачивание файла
- [x] Скачивание директории
- [x] Скачивание несуществующего объекта
- [x] Переименование файла
- [x] Перемещение файла
- [x] Конфликт при перемещении (409)
- [x] Перемещение несуществующего объекта (404)
- [x] Поиск файлов
- [x] Поиск с пустым запросом
- [x] Загрузка одного файла
- [x] Загрузка нескольких файлов
- [x] Конфликт при загрузке (409)
- [x] Информация о несуществующей директории (404)
- [ ] Тесты без регистрации (401) - требуют донастройки

### Заметки по итогам

- **Логирование** — незаменимо при отладке, особенно в связке с авто-тестами
- **Выброс исключений** Checked & Unchecked exceptions:
  > Checked (extends Exception) — нужно объявлять в throws, подходит для recoverable ошибок.
  > Unchecked (extends RuntimeException) — можно не объявлять
  > Лучше разобрался, но код местами грязный
- **Валидация путей** — унифицирована, добавлена проверка ожидаемого типа ресурса
- **Тесты** — ручные проходят, автоматические требуют изоляции (Testcontainers или H2 в следующий раз)

### Что осталось

1. Проверки безопасности: кейс `uploadToNonexistentPath` — не приводит ли к перезаписи
2. Добавить валидатор на переименования множества ресурсов, вроде примера ниже. Переименовывать можно только 1 ресурс - 1 папку или 1 файл.

```json
// fixme: PATCH `resourse/move`
{
  "from": "test-move-files/source/test-file.txt",
  "to": "renamed-file.txt"
}
```

3. Доработка ресурсов - использовать поля "lastModified", "downloadUrl", "contentType"
4. Ф̶р̶о̶н̶т̶е̶н̶д̶:̶ ̶п̶о̶с̶л̶е̶ ̶у̶н̶и̶ф̶и̶к̶а̶ц̶и̶и̶ ̶б̶е̶з̶ ̶в̶е̶д̶у̶щ̶е̶г̶о̶ ̶с̶л̶е̶ш̶а̶ ̶п̶е̶р̶е̶с̶т̶а̶л̶и̶ ̶в̶ы̶д̶е̶л̶я̶т̶ь̶с̶я̶ ̶ф̶а̶й̶л̶ы̶ ̶и̶ ̶п̶а̶п̶к̶и̶ ̶в̶ ̶к̶о̶р̶н̶е̶  -> ✅ ошибка тянулась с бэка, из-за того что я решил родительскую папку для файлов из корня возвращать как "/", пофиксил
5. Ф̶р̶о̶н̶т̶е̶н̶д̶:̶ ̶и̶с̶п̶о̶л̶ь̶з̶о̶в̶а̶т̶ь̶ ̶`̶/̶h̶e̶a̶l̶t̶h̶`̶ ̶э̶н̶д̶п̶о̶и̶н̶т̶ ̶и̶ ̶д̶о̶б̶а̶в̶и̶т̶ь̶ ̶m̶a̶i̶n̶t̶e̶n̶c̶e̶ ̶с̶т̶р̶а̶н̶и̶ц̶ы̶ ̶п̶р̶и̶ н̶е̶д̶о̶с̶т̶у̶п̶н̶о̶с̶т̶и̶ ̶с̶е̶р̶в̶и̶с̶о̶в̶ ✅
6. Фронтенд эджкейс: невозможно загрузить одну и ту же папку в разные папки (пока лечится перезагрузкой страницы)

- Опционально: переписать бэк на nodejs/laravel для сравнения производительности, переписать фронт на Vue

## Предыдущий статус (этап 2)

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

### 3. Исключения (структура)

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

### 4. Добавлено тестирование

#### Работающие проверки в тестах

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
