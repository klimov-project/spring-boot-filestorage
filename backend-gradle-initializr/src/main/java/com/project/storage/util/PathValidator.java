package com.project.storage.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.project.storage.model.ResourceType;

@Component
public class PathValidator {

    /**
     * Проверяет, является ли путь корректным и определяет тип ресурса
     *
     * @param rawPath сырой путь (может быть null)
     * @return тип ресурса или null если путь невалидный
     */
    public ResourceType validateAndGetType(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }

        String path = rawPath.trim();

        // Путь должен начинаться не с / (относительный путь от корня пользователя)
        if (path.startsWith("/") || path.startsWith("\\")) {
            return null;
        }

        // Проверяем завершающий слэш для определения типа
        boolean isDirectory = path.endsWith("/");

        // Базовые проверки безопасности пути
        try {
            Path normalized = Paths.get(path).normalize();
            String normalizedStr = normalized.toString();

            // Защита от path traversal (../)
            if (normalizedStr.contains("..")) {
                return null;
            }

            // Для директории возвращаем тип с учетом слэша
            if (isDirectory) {
                return ResourceType.DIRECTORY;
            } else {
                return ResourceType.FILE;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Извлекает имя ресурса из полного пути
     */
    public String extractName(String fullPath) {
        if (!StringUtils.hasText(fullPath)) {
            return "";
        }

        String path = fullPath.trim();
        boolean isDirectory = path.endsWith("/");

        if (isDirectory) {
            // Убираем завершающий слэш
            path = path.substring(0, path.length() - 1);
        }

        int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSeparator == -1) {
            return path; // Нет разделителей - вся строка это имя
        }

        return path.substring(lastSeparator + 1);
    }

    /**
     * Извлекает путь к родительской папке
     */
    public String extractParentPath(String fullPath) {
        if (!StringUtils.hasText(fullPath)) {
            return "";
        }

        String path = fullPath.trim();
        boolean isDirectory = path.endsWith("/");

        if (!isDirectory) {
            // Для файла ищем последний разделитель
            int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            if (lastSeparator == -1) {
                return ""; // Нет родительской папки (корень)
            }
            return path.substring(0, lastSeparator + 1);
        } else {
            // Для папки убираем последний элемент пути
            String withoutTrailingSlash = path.substring(0, path.length() - 1);
            int lastSeparator = Math.max(withoutTrailingSlash.lastIndexOf('/'),
                    withoutTrailingSlash.lastIndexOf('\\'));
            if (lastSeparator == -1) {
                return ""; // Корневая директория
            }
            return withoutTrailingSlash.substring(0, lastSeparator + 1);
        }
    }
}
