package com.project.storage.service;

import java.util.List;
import com.project.entity.MinioObject;
import com.project.exception.StorageException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

@Component
public class MinioServiceAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MinioServiceAdapter.class);

    private final MinioService minioService;

    public MinioServiceAdapter(MinioService minioService) {
        this.minioService = minioService;
    }

    // ============= АДАПТЕРЫ ДЛЯ МЕТОДОВ MINIOSERVICE =============
    /**
     * Создание папки с преобразованием исключений
     */
    public void createFolder(Long userId, String fullPath) {
        createFolder(userId, fullPath, true);
    }

    public void createFolder(Long userId, String relativePath, boolean strict) {
        String fullPath = toFullPath(userId, relativePath);
        logger.debug("Creating folder - userId: {}, relativePath: {}, fullPath: {}",
                userId, relativePath, fullPath);

        try {
            minioService.createFolder(fullPath, strict);
        } catch (Exception e) {
            throw transformCreateFolderException(e, userId, relativePath);
        }
    }

    /**
     * Получение информации об объекте с преобразованием исключений
     */
    public MinioObject getObjectInfo(Long userId, String relativePath) {
        String fullPath = toFullPath(userId, relativePath);

        try {
            return minioService.getObjectInfo(fullPath);
        } catch (Exception e) {
            throw transformGetObjectInfoException(e, userId, relativePath);
        }
    }

    /**
     * Загрузка файлов с преобразованием исключений
     */
    public List<MinioObject> uploadFiles(
            Long userId,
            String destinationRelativePath,
            org.springframework.web.multipart.MultipartFile[] files) {

        String destinationFullPath = toFullPath(userId, destinationRelativePath);

        try {
            return minioService.uploadFiles(destinationFullPath, files);
        } catch (Exception e) {
            throw transformUploadFilesException(e, userId, destinationRelativePath);
        }
    }

    /**
     * Удаление объекта с преобразованием исключений
     */
    public void deleteObject(Long userId, String relativePath) {
        String fullPath = toFullPath(userId, relativePath);

        try {
            minioService.deleteObject(fullPath);
        } catch (Exception e) {
            throw transformDeleteObjectException(e, userId, relativePath);
        }
    }

    /**
     * Переименование объекта с преобразованием исключений
     */
    public void renameObject(Long userId, String fromRelativePath, String toRelativePath) {
        String fromFullPath = toFullPath(userId, fromRelativePath);
        String toFullPath = toFullPath(userId, toRelativePath);

        try {
            minioService.renameObject(fromFullPath, toFullPath);
        } catch (Exception e) {
            throw transformRenameObjectException(e, userId, fromRelativePath, toRelativePath);
        }
    }

    /**
     * Поиск файлов с преобразованием исключений
     */
    public List<MinioObject> searchFiles(
            Long userId,
            String query) {

        String userFolder = "user-" + userId + "-files";

        try {
            return minioService.searchFiles(userFolder, query);
        } catch (Exception e) {
            throw transformSearchFilesException(e, userId, query);
        }
    }

    /**
     * Получение списка объектов с преобразованием исключений
     */
    public List<MinioObject> listObjects(
            Long userId,
            String relativePath) {

        try {
            validateRequestedResource(userId, relativePath);

            String fullPath = toFullPath(userId, relativePath);
            logger.info("listObjects for user {} fullPath: '{}'", userId, fullPath);
            return minioService.listObjects(fullPath);
        } catch (Exception e) {
            throw transformListObjectsException(e, userId, relativePath);
        }
    }

    /**
     * Проверка существования объекта с преобразованием исключений
     */
    public boolean isObjectExists(Long userId, String relativePath) {
        String fullPath = toFullPath(userId, relativePath);

        try {
            return minioService.isObjectExists(fullPath);
        } catch (Exception e) {
            throw transformObjectExistsException(e, userId, relativePath);
        }
    }

    /**
     * Получение ресурса для скачивания с преобразованием исключений
     */
    public DownloadService.DownloadResult getDownloadResource(Long userId, String relativePath) throws IOException {
        logger.info("Adapter: Preparing download for user {} path: {}", userId, relativePath);

        // Валидация пути через адаптер
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new StorageException.InvalidPathException(
                    "Путь не может быть пустым",
                    userId,
                    relativePath,
                    "getDownloadResource"
            );
        }

        try {
            // Проверяем существование объекта
            isObjectExists(userId, relativePath);

            // Получаем информацию о ресурсе
            MinioObject objectInfo = getObjectInfo(userId, relativePath);

            // Определяем тип и готовим результат
            boolean isDirectory = objectInfo.isDirectory() || relativePath.endsWith("/");

            // Создаем DownloadResult через вызов сервиса скачивания
            // Здесь мы не создаем сам ресурс, а возвращаем информацию для MinioDownloadService
            return new DownloadService.DownloadResult(null, null, false); // Заглушка, реальный результат создается в MinioDownloadService

        } catch (Exception e) {
            throw transformGetDownloadResourceException(e, userId, relativePath);
        }
    }

    /**
     * Получение URL для скачивания с преобразованием исключений
     */
    public String getDownloadUrl(Long userId, String relativePath) {
        String fullPath = toFullPath(userId, relativePath);

        try {
            return minioService.getDownloadUrl(fullPath);
        } catch (Exception e) {
            throw transformGetDownloadUrlException(e, userId, relativePath);
        }
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============
    /**
     * Преобразование относительного пути в полный
     */
    private String toFullPath(Long userId, String relativePath) {
        String userPrefix = "user-" + userId + "-files/";

        if (relativePath == null || relativePath.isEmpty() || "/".equals(relativePath)) {
            return userPrefix;
        }

        String cleanPath = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;

        return userPrefix + cleanPath;
    }

    /**
     * Извлечение родительского пути
     */
    private String extractParentPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }

        String cleanPath = path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;

        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }

        return cleanPath.substring(0, lastSlash);
    }

    /**
     * Валидация запрашиваемого ресурса
     */
    private void validateRequestedResource(Long userId, String relativePath) {

        if (!isObjectExists(userId, relativePath)) {
            throw new StorageException.ResourceNotFoundException(
                    "Ресурс не найден: " + relativePath,
                    userId,
                    relativePath,
                    "validateRequestedResource"
            );
        }
    }

    // ============= ТРАНСФОРМАЦИЯ ИСКЛЮЧЕНИЙ =============
    private RuntimeException transformCreateFolderException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform createFolder exception: {}", errorMessage);

        if (errorMessage != null) {
            if (errorMessage.contains("Папка уже существует")
                    || errorMessage.contains("Path already exists")) {
                return new StorageException.ResourceAlreadyExistsException(
                        "Папка уже существует: " + relativePath,
                        userId,
                        relativePath,
                        "createFolder"
                );
            } else if (errorMessage.contains("Родительская папка не существует")) {
                String parentPath = extractParentPath(relativePath);
                return new StorageException.ResourceNotFoundException(
                        "Родительская папка не существует: " + parentPath,
                        userId,
                        parentPath,
                        "createFolder"
                );
            } else if (errorMessage.contains("Невалидный путь")) {
                return new StorageException.InvalidPathException(
                        "Невалидный путь: " + relativePath,
                        userId,
                        relativePath,
                        "createFolder"
                );
            }
        }

        return new StorageException.StorageOperationException(
                "Ошибка при создании папки: " + e.getMessage(),
                userId,
                relativePath,
                "createFolder"
        );
    }

    private RuntimeException transformUploadFilesException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform uploadFiles exception: {}", errorMessage);

        if (errorMessage != null && errorMessage.contains("File already exists")) {
            return new StorageException.ResourceAlreadyExistsException(
                    "Файл уже существует в папке назначения: " + relativePath,
                    userId,
                    relativePath,
                    "uploadFiles"
            );
        }

        return new StorageException.StorageOperationException(
                "Ошибка при загрузке файлов: " + e.getMessage(),
                userId,
                relativePath,
                "uploadFiles"
        );
    }

    private RuntimeException transformGetObjectInfoException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform getObjectInfo exception: {}", errorMessage);

        if (errorMessage != null && (errorMessage.contains("Ресурс не найден")
                || errorMessage.contains("NoSuchKey")
                || errorMessage.contains("Not Found"))) {

            return new StorageException.ResourceNotFoundException(
                    "Ресурс не найден: " + relativePath,
                    userId,
                    relativePath,
                    "getObjectInfo"
            );
        }

        return new StorageException.StorageOperationException(
                "Ошибка при получении информации о ресурсе: " + e.getMessage(),
                userId,
                relativePath,
                "getObjectInfo"
        );
    }

    private RuntimeException transformDeleteObjectException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform deleteObject exception: {}", errorMessage);

        if (errorMessage != null && (errorMessage.contains("Ресурс не найден")
                || errorMessage.contains("NoSuchKey"))) {

            return new StorageException.ResourceNotFoundException(
                    "Ресурс не найден: " + relativePath,
                    userId,
                    relativePath,
                    "deleteObject"
            );
        }

        return new StorageException.StorageOperationException(
                "Ошибка при удалении ресурса: " + e.getMessage(),
                userId,
                relativePath,
                "deleteObject"
        );
    }

    private RuntimeException transformRenameObjectException(
            Exception e, Long userId, String fromRelativePath, String toRelativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform renameObject exception: {}", errorMessage);

        if (errorMessage != null && (errorMessage.contains("Ресурс не найден")
                || errorMessage.contains("NoSuchKey"))) {

            return new StorageException.ResourceNotFoundException(
                    "Ресурс не найден: " + fromRelativePath,
                    userId,
                    fromRelativePath,
                    "renameObject"
            );
        } else if (errorMessage != null && errorMessage.contains("уже существует")) {
            return new StorageException.ResourceAlreadyExistsException(
                    "Ресурс уже существует: " + toRelativePath,
                    userId,
                    toRelativePath,
                    "renameObject"
            );
        }

        return new StorageException.StorageOperationException(
                "Ошибка при перемещении ресурса: " + e.getMessage(),
                userId,
                fromRelativePath,
                "renameObject"
        );
    }

    private RuntimeException transformSearchFilesException(
            Exception e, Long userId, String query) {

        return new StorageException.StorageOperationException(
                "Ошибка при поиске файлов: " + e.getMessage(),
                userId,
                null,
                "searchFiles"
        );
    }

    private RuntimeException transformListObjectsException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform listObjects exception: {}", errorMessage);

        if (errorMessage != null && (errorMessage.contains("Ресурс не найден")
                || errorMessage.contains("NoSuchKey"))) {

            return new StorageException.ResourceNotFoundException(
                    "Папка не существует: " + relativePath,
                    userId,
                    relativePath,
                    "listObjects"
            );
        }

        return new StorageException.StorageOperationException(
                "Ошибка при получении содержимого папки: " + e.getMessage(),
                userId,
                relativePath,
                "listObjects"
        );
    }

    private RuntimeException transformObjectExistsException(
            Exception e, Long userId, String relativePath) {

        return new StorageException.StorageOperationException(
                "Ошибка при проверке существования объекта: " + e.getMessage(),
                userId,
                relativePath,
                "objectExists"
        );
    }

    private RuntimeException transformGetDownloadUrlException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform getDownloadUrl exception: {}", errorMessage);

        if (errorMessage != null && (errorMessage.contains("Ресурс не найден")
                || errorMessage.contains("NoSuchKey"))) {

            return new StorageException.ResourceNotFoundException(
                    "Ресурс не найден: " + relativePath,
                    userId,
                    relativePath,
                    "getDownloadUrl"
            );
        }

        return new StorageException.StorageOperationException(
                "Ошибка при генерации ссылки для скачивания: " + e.getMessage(),
                userId,
                relativePath,
                "getDownloadUrl"
        );
    }

    private RuntimeException transformGetDownloadResourceException(
            Exception e, Long userId, String relativePath) {

        String errorMessage = e.getMessage();
        logger.debug("Transform getDownloadResource exception: {}", errorMessage);

        if (errorMessage != null) {
            if (errorMessage.contains("Ресурс не найден")
                    || errorMessage.contains("NoSuchKey")
                    || errorMessage.contains("Not Found")) {
                return new StorageException.ResourceNotFoundException(
                        "Ресурс не найден: " + relativePath,
                        userId,
                        relativePath,
                        "getDownloadResource"
                );
            } else if (errorMessage.contains("Невалидный путь")
                    || errorMessage.contains("Invalid path")) {
                return new StorageException.InvalidPathException(
                        "Невалидный путь: " + relativePath,
                        userId,
                        relativePath,
                        "getDownloadResource"
                );
            }
        }

        return new StorageException.StorageOperationException(
                "Ошибка при подготовке скачивания: " + e.getMessage(),
                userId,
                relativePath,
                "getDownloadResource"
        );
    }
}
