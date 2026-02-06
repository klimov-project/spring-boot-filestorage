package com.project.storage.service;

import com.project.exception.StorageException;
import com.project.entity.MinioObject;
import com.project.service.MinioService;
import com.project.service.StorageService;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.model.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MinioStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);
    private final MinioService minioService;

    public MinioStorageService(MinioService minioService) {
        this.minioService = minioService;
    }

    @Override
    public void createUserDirectory(Long userId) {
        String userRootFolder = getUserFolderPath(userId) + "/";
        try {
            minioService.createFolder(userRootFolder);
        } catch (Exception e) {
            throw new StorageException.StorageOperationException(
                    "Failed to create user directory for user " + userId + ": " + e.getMessage(),
                    userId,
                    "/",
                    "createUserDirectory"
            );
        }
    }

    @Override
    public ResourceInfo createDirectory(Long userId, String relativePath) {
        validatePath(relativePath);

        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new StorageException.InvalidPathException(
                    "Путь не может быть пустым",
                    userId,
                    relativePath,
                    "createDirectory"
            );
        }

        String fullPath = getFullPath(userId, relativePath);

        // Добавляем слэш для папок
        if (!fullPath.endsWith("/")) {
            fullPath = fullPath + "/";
        }

        try {
            minioService.createFolder(fullPath);
            return getResourceInfo(userId, relativePath);
        } catch (RuntimeException e) {
            handleMinioException(e, userId, relativePath, "createDirectory");
            return null; // Never reached
        }
    }

    @Override
    public ResourceInfo getResourceInfo(Long userId, String relativePath) {
        validatePath(relativePath);

        // Обработка корневой директории
        if ("/".equals(relativePath) || relativePath == null || relativePath.isEmpty()) {
            return ResourceInfo.builder()
                    .path("/")
                    .name("root")
                    .type(ResourceType.DIRECTORY)
                    .userId(userId)
                    .build();
        }

        String fullPath = getFullPath(userId, relativePath);

        try {
            MinioObject object = minioService.getObjectInfo(fullPath);
            return convertToResourceInfo(userId, object);
        } catch (RuntimeException e) {
            handleMinioException(e, userId, relativePath, "getResourceInfo");
            return null; // Never reached
        }
    }

    @Override
    public List<ResourceInfo> uploadFiles(Long userId, String destinationRelativePath, MultipartFile[] files) {
        validatePath(destinationRelativePath);

        if (files == null || files.length == 0) {
            throw new StorageException.InvalidPathException(
                    "Не указаны файлы для загрузки",
                    userId,
                    destinationRelativePath,
                    "uploadFiles"
            );
        }

        String destinationFullPath = getFullPath(userId, destinationRelativePath);

        try {
            List<MinioObject> uploaded = minioService.uploadFiles(destinationFullPath, files);
            return uploaded.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            handleMinioException(e, userId, destinationRelativePath, "uploadFiles");
            return null; // Never reached
        }
    }

    @Override
    public void deleteResource(Long userId, String relativePath) {
        validatePath(relativePath);

        String fullPath = getFullPath(userId, relativePath);

        try {
            // Проверяем существование перед удалением
            minioService.getObjectInfo(fullPath);
            minioService.deleteObject(fullPath);
        } catch (RuntimeException e) {
            handleMinioException(e, userId, relativePath, "deleteResource");
        }
    }

    @Override
    public ResourceInfo moveResource(Long userId, String fromRelativePath, String toRelativePath) {
        validatePath(fromRelativePath);
        validatePath(toRelativePath);

        String fromFullPath = getFullPath(userId, fromRelativePath);
        String toFullPath = getFullPath(userId, toRelativePath);

        try {
            // Проверяем существование исходного ресурса
            minioService.getObjectInfo(fromFullPath);

            // Проверяем, не существует ли уже целевой ресурс
            try {
                minioService.getObjectInfo(toFullPath);
                throw new StorageException.ResourceAlreadyExistsException(
                        "Ресурс уже существует: " + toRelativePath,
                        userId,
                        toRelativePath,
                        "moveResource"
                );
            } catch (RuntimeException ex) {
                // Ресурс не существует - можно продолжать
                if (!isNotFoundException(ex)) {
                    // Если это не "не найден", значит другая ошибка
                    handleMinioException(ex, userId, toRelativePath, "moveResource");
                }
            }

            minioService.renameObject(fromFullPath, toFullPath);
            return getResourceInfo(userId, toRelativePath);
        } catch (StorageException ex) {
            throw ex; // Пробрасываем как есть
        } catch (RuntimeException e) {
            handleMinioException(e, userId, fromRelativePath, "moveResource");
            return null; // Never reached
        }
    }

    @Override
    public List<ResourceInfo> searchResources(Long userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new StorageException.InvalidPathException(
                    "Поисковый запрос не может быть пустым",
                    userId,
                    null,
                    "searchResources"
            );
        }

        String userFolder = getUserFolderPath(userId);
        try {
            List<MinioObject> results = minioService.searchFiles(userFolder, query);
            return results.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw new StorageException.StorageOperationException(
                    "Ошибка при поиске: " + e.getMessage(),
                    userId,
                    null,
                    "searchResources"
            );
        }
    }

    @Override
    public List<ResourceInfo> getDirectoryContents(Long userId, String relativePath) {
        validatePath(relativePath);

        String fullPath = getFullPath(userId, relativePath);

        try {
            List<MinioObject> objects = minioService.listObjects(fullPath);
            return objects.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            handleMinioException(e, userId, relativePath, "getDirectoryContents");
            return null; // Never reached
        }
    }

    // ============= ОБРАБОТКА ИСКЛЮЧЕНИЙ =============
    private void handleMinioException(RuntimeException e, Long userId, String relativePath, String operation) {
        String errorMsg = e.getMessage();

        logger.error("Minio exception during {} for user {}: {}", operation, userId, errorMsg, e);

        if (errorMsg != null) {
            // Проверяем известные шаблоны ошибок
            if (errorMsg.contains("Папка уже существует")
                    || errorMsg.contains("уже существует")
                    || errorMsg.contains("CONFLICT")) {
                throw new StorageException.ResourceAlreadyExistsException(
                        errorMsg, userId, relativePath, operation
                );
            } else if (errorMsg.contains("Родительская папка не существует")
                    || errorMsg.contains("Ресурс не найден")
                    || errorMsg.contains("NoSuchKey")
                    || errorMsg.contains("Not Found")) {
                throw new StorageException.ResourceNotFoundException(
                        errorMsg, userId, relativePath, operation
                );
            } else if (errorMsg.contains("Невалидный путь")
                    || errorMsg.contains("Invalid path")) {
                throw new StorageException.InvalidPathException(
                        errorMsg, userId, relativePath, operation
                );
            }
        }

        // Если не распознали - бросаем общую ошибку операции
        throw new StorageException.StorageOperationException(
                "Ошибка при выполнении операции '" + operation + "': " + e.getMessage(),
                userId,
                relativePath,
                operation
        );
    }

    private boolean isNotFoundException(RuntimeException e) {
        String errorMsg = e.getMessage();
        return errorMsg != null && (errorMsg.contains("Ресурс не найден")
                || errorMsg.contains("NoSuchKey")
                || errorMsg.contains("Not Found"));
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (остаются без изменений) =============
    private void validatePath(String path) {
        if (path == null) {
            return;
        }

        if (path.contains("..")) {
            throw new StorageException.InvalidPathException(
                    "Путь содержит недопустимые символы '..'",
                    null,
                    path,
                    "validatePath"
            );
        }
    }

    private String getFullPath(Long userId, String relativePath) {
        String userFolder = getUserFolderPath(userId) + "/";

        if (relativePath == null || relativePath.isEmpty() || "/".equals(relativePath)) {
            return userFolder;
        }

        String cleanPath = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;

        return userFolder + cleanPath;
    }

    private String getRelativePath(Long userId, String fullPath) {
        String userPrefix = getUserFolderPath(userId) + "/";

        if (fullPath.startsWith(userPrefix)) {
            return fullPath.substring(userPrefix.length());
        }

        return fullPath;
    }

    private String getUserFolderPath(Long userId) {
        return "user-" + userId + "-files";
    }

    private String getParentPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }

        String cleanPath = path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;

        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash == -1) {
            return "/";
        }

        return cleanPath.substring(0, lastSlash + 1);
    }

    private ResourceInfo convertToResourceInfo(Long userId, MinioObject minioObject) {
        String fullPath = minioObject.getPath();
        String relativePath = getRelativePath(userId, fullPath);
        String name = extractNameFromPath(relativePath);
        String parentPath = getParentPath(relativePath);

        ResourceType type = minioObject.isDirectory()
                ? ResourceType.DIRECTORY
                : ResourceType.FILE;

        return ResourceInfo.builder()
                .path(parentPath)
                .name(name)
                .size(type == ResourceType.FILE ? minioObject.getSize() : null)
                .type(type)
                .userId(userId)
                .build();
    }

    private String extractNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSlash = path.lastIndexOf('/');
        return lastSlash != -1 ? path.substring(lastSlash + 1) : path;
    }
}
