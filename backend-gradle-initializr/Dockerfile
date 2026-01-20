# Этап 1: Сборка (builder) с оптимизированным кешированием
FROM gradle:8.7-jdk17-alpine AS builder

# Устанавливаем рабочую директорию
WORKDIR /app

# 1. Копируем только файлы конфигурации
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# 2. Делаем gradlew исполняемым
RUN chmod +x gradlew

# 3. Скачиваем зависимости один раз (кешируется)
# Флаг --no-daemon важен для Docker
RUN ./gradlew dependencies --no-daemon

# 4. Копируем исходный код после скачивания зависимостей
COPY src ./src

# 5. Собираем приложение
RUN ./gradlew clean build -x test --no-daemon

# Этап 2: Запуск (runtime) - оставляем как есть
FROM openjdk:17-jdk-slim

# Создаём пользователя для безопасности
RUN groupadd -r spring && useradd --no-log-init -r -g spring spring
USER spring:spring

# Рабочая директория
WORKDIR /app

# Копируем JAR из этапа сборки
COPY --from=builder /app/build/libs/*.jar app.jar

# Порт приложения
EXPOSE 8080

# Команда запуска
ENTRYPOINT ["java", "-jar", "app.jar"]
