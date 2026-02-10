# Docker Compose Configuration Guide

## Files

- **docker-compose.prod.yml** - Безопасный файл для публичного репозитория с env переменными
- **.env.example** - Шаблон переменных окружения (для копирования)
- **.env** - Реальный файл с конфиденциальными данными (в .gitignore)

## Использование

### 1. Создание локального .env файла

```bash
# Скопируй шаблон
cp .env.example .env

# Отредактируй значения в .env (замени пароли на безопасные)
```

### 2. Запуск контейнеров

```bash
# Использует переменные из .env файла автоматически
docker-compose -f docker-compose.prod.yml up -d
```

### 3. Переменные окружения

| Переменная            | Описание           | Пример          |
| --------------------- | ------------------ | --------------- |
| POSTGRES_DB           | Имя БД             | appdb           |
| POSTGRES_USER         | Пользователь БД    | appuser         |
| POSTGRES_PASSWORD     | Пароль БД          | secure_password |
| POSTGRES_PORT         | Порт PostgreSQL    | 5432            |
| REDIS_PORT            | Порт Redis         | 6379            |
| MINIO_ROOT_USER       | Пользователь MinIO | minioadmin      |
| MINIO_ROOT_PASSWORD   | Пароль MinIO       | secure_password |
| MINIO_DEFAULT_BUCKETS | Buckets MinIO      | user-uploads    |

## Безопасность

    ✅ Пароли не хранятся в репозитории (.env в .gitignore)
    ✅ .env.example служит шаблоном для новых разработчиков
    ✅ docker-compose.prod.yml безопасно выкладывается в репозиторий
    ✅ Все sensitive данные через переменные окружения

## Для production

Используй переменные окружения ОС или CI/CD секреты (GitHub Actions, GitLab CI и т.д.)

```bash
export POSTGRES_PASSWORD="your-production-password"
export MINIO_ROOT_PASSWORD="your-production-password"
docker-compose -f docker-compose.prod.yml up -d
```
