package com.project.storage.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.project.storage.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PathValidator {

    private static final String INVALID_CHARS_REGEX = "[/\\\\?*:\"<>|]";
    private static final int MAX_NAME_LENGTH = 128;

    private static final Logger logger = LoggerFactory.getLogger(PathValidator.class);
    private boolean validatePath(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        if (name.trim().isEmpty()) {
            return false;
        }
        if (name.startsWith(".")) {
            return false; // скрытые файлы/папки не разрешаем

        }
        if (name.endsWith(" ") || name.endsWith(".")) {
            return false;
        }
        if (name.matches(".*" + INVALID_CHARS_REGEX + ".*")) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (c < 32) {
                return false; // Символы управления

            }
        }
        return true;
    }

    public boolean validateFilePath(String path) {
        String name = extractName(path);
        return validatePath(name);
    }

    public boolean validateFolderPath(String path) {
        String cleanPath = path;
        if (cleanPath != null && cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        String name = extractName(cleanPath);
        return validatePath(name);
    }

    /**
     * Проверяет, является ли путь корректным и определяет тип ресурса
     *
     * @param rawPath сырой путь (может быть null)
     * @return тип ресурса или null если путь невалидный
     */
    public ResourceType validateAndGetType(String rawPath) {
        logger.info("validateAndGetType rawPath: '{}'", rawPath);
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }

        String path = rawPath.trim();

        logger.info("String path: '{}'", path);
        // Если путь равен "/", то это корень пользовательской папки
        if (path.equals("/")) {
            return ResourceType.DIRECTORY;
        }
        logger.info("path.equals PASSED path: '{}'", path);

        // Путь не должен начинаться с / или \ (относительный путь от корня пользователя)
        if (path.startsWith("/") || path.startsWith("\\")) {
            return null;
        }

        logger.info("path.startsWith PASSED2 path: '{}'", path);
        // Проверяем завершающий слэш для определения типа
        boolean isDirectory = path.endsWith("/");

        logger.info("boolean isDirectory PASSED3 path: '{}'", path);
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
                if (!validateFolderPath(path)) {
                    return null;
                }
                return ResourceType.DIRECTORY;
            } else {
                if (!validateFilePath(path)) {
                    return null;
                }
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

    /**
     * Проверяет, что тип ресурса сохранился при переименовании
     */
    public boolean isSameResourceType(String fromPath, String toPath) {
        ResourceType fromType = validateAndGetType(fromPath);
        ResourceType toType = validateAndGetType(toPath);
        return fromType != null && toType != null && fromType == toType;
    }

    /**
     * Проверяет, является ли перемещение просто переименованием (в той же
     * директории)
     */
    public boolean isRenameOnly(String fromPath, String toPath) {
        String fromParent = extractParentPath(fromPath);
        String toParent = extractParentPath(toPath);
        return fromParent.equals(toParent);
    }

    /**
     * Проверяет, является ли перемещение просто сменой пути (то же имя в другой
     * директории)
     */
    public boolean isPathChangeOnly(String fromPath, String toPath) {
        String fromName = extractName(fromPath);
        String toName = extractName(toPath);
        return fromName.equals(toName);
    }

    /**
     * Нормализует путь для сравнения
     */
    public String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        try {
            Path normalized = Paths.get(path).normalize();
            String result = normalized.toString();

            // Восстанавливаем завершающий слэш для директорий
            if (path.endsWith("/") && !result.endsWith("/")) {
                result = result + "/";
            }
            return result;
        } catch (Exception e) {
            return path;
        }
    }
}
