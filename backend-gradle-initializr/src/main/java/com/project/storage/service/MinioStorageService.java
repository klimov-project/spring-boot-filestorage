package com.project.storage.service;

import com.project.entity.MinioObject;
import com.project.service.MinioService;
import com.project.service.StorageService;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.exception.StorageException;
import com.project.storage.model.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MinioStorageService implements StorageService {

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
            throw new RuntimeException("Failed to create user directory for user " + userId, e);
        }
    }

    @Override
    public ResourceInfo createDirectory(Long userId, String relativePath) {
        validatePath(relativePath);

        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new StorageException.InvalidPathException(
                    "Путь не может быть пустым", userId, relativePath
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
            // Преобразуем исключения MinioService
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("Папка уже существует")) {
                    throw new StorageException.ResourceAlreadyExistsException(
                            "Папка уже существует: " + relativePath, userId, relativePath
                    );
                } else if (errorMsg.contains("Родительская папка не существует")) {
                    throw new StorageException.ResourceNotFoundException(
                            "Родительская папка не существует: " + getParentPath(relativePath),
                            userId, getParentPath(relativePath)
                    );
                } else if (errorMsg.contains("Невалидный путь")) {
                    throw new StorageException.InvalidPathException(
                            "Невалидный путь: " + relativePath, userId, relativePath
                    );
                }
            }
            throw new RuntimeException("Ошибка при создании папки: " + e.getMessage(), e);
        }
    }

    @Override
    public ResourceInfo getResourceInfo(Long userId, String relativePath) {
        validatePath(relativePath);

        String fullPath = getFullPath(userId, relativePath);

        // Обработка корневой директории
        if ("/".equals(relativePath) || relativePath == null || relativePath.isEmpty()) {
            return ResourceInfo.builder()
                    .path("/")
                    .name("root")
                    .type(ResourceType.DIRECTORY)
                    .userId(userId)
                    .build();
        }

        try {
            MinioObject object = minioService.getObjectInfo(fullPath);
            return convertToResourceInfo(userId, object);
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Ресурс не найден")
                    || errorMsg.contains("NoSuchKey"))) {
                throw new StorageException.ResourceNotFoundException(
                        "Ресурс не найден: " + relativePath, userId, relativePath
                );
            }
            throw new RuntimeException("Ошибка при получении информации о ресурсе: " + e.getMessage(), e);
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
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Ресурс не найден")
                    || errorMsg.contains("NoSuchKey"))) {
                throw new StorageException.ResourceNotFoundException(
                        "Ресурс не найден: " + relativePath, userId, relativePath
                );
            }
            throw new RuntimeException("Ошибка при удалении ресурса: " + e.getMessage(), e);
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
                        "Ресурс уже существует: " + toRelativePath, userId, toRelativePath
                );
            } catch (RuntimeException ex) {
                // Ресурс не существует - можно продолжать
            }

            minioService.renameObject(fromFullPath, toFullPath);
            return getResourceInfo(userId, toRelativePath);
        } catch (StorageException.ResourceAlreadyExistsException ex) {
            throw ex; // Пробрасываем как есть
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Ресурс не найден")) {
                throw new StorageException.ResourceNotFoundException(
                        "Ресурс не найден: " + fromRelativePath, userId, fromRelativePath
                );
            }
            throw new RuntimeException("Ошибка при перемещении ресурса: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ResourceInfo> searchResources(Long userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new StorageException.InvalidPathException(
                    "Поисковый запрос не может быть пустым", userId, null
            );
        }

        String userFolder = getUserFolderPath(userId);
        List<MinioObject> results = minioService.searchFiles(userFolder, query);

        return results.stream()
                .map(obj -> convertToResourceInfo(userId, obj))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceInfo> uploadFiles(Long userId, String destinationRelativePath, MultipartFile[] files) {
        validatePath(destinationRelativePath);

        if (files == null || files.length == 0) {
            throw new StorageException.InvalidPathException(
                    "Не указаны файлы для загрузки", userId, destinationRelativePath
            );
        }

        String destinationFullPath = getFullPath(userId, destinationRelativePath);

        try {
            List<MinioObject> uploaded = minioService.uploadFiles(destinationFullPath, files);
            return uploaded.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("уже существует")
                    || errorMsg.contains("CONFLICT"))) {
                throw new StorageException.ResourceAlreadyExistsException(
                        "Файл уже существует в папке назначения", userId, destinationRelativePath
                );
            }
            throw new RuntimeException("Ошибка при загрузке файлов: " + e.getMessage(), e);
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
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Ресурс не найден")
                    || errorMsg.contains("NoSuchKey"))) {
                throw new StorageException.ResourceNotFoundException(
                        "Папка не существует: " + relativePath, userId, relativePath
                );
            }
            throw new RuntimeException("Ошибка при получении содержимого папки: " + e.getMessage(), e);
        }
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============
    private void validatePath(String path) {
        if (path == null) {
            return;
        }

        if (path.contains("..")) {
            throw new StorageException.InvalidPathException(
                    "Путь содержит недопустимые символы '..'", null, path
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
