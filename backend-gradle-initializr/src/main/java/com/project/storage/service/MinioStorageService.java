package com.project.storage.service;

import com.project.entity.MinioObject;
import com.project.exception.StorageException;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.model.ResourceType;
import com.project.storage.util.PathValidator;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MinioStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);
    private final MinioServiceAdapter minioServiceAdapter;
    private final PathValidator pathValidator;

    public MinioStorageService(MinioServiceAdapter minioServiceAdapter, PathValidator pathValidator) {
        this.minioServiceAdapter = minioServiceAdapter;
        this.pathValidator = pathValidator;
    }

    @Override
    public void createUserDirectory(Long userId) {
        try {
            // Создаем корневую папку пользователя
            minioServiceAdapter.createFolder(userId, "/");
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public ResourceInfo createDirectory(Long userId, String relativePath) {

        try {
            minioServiceAdapter.createFolder(userId, relativePath);
            return getResourceInfo(userId, relativePath);
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public ResourceInfo getResourceInfo(Long userId, String relativePath) {

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
            MinioObject object = minioServiceAdapter.getObjectInfo(userId, relativePath);
            return convertToResourceInfo(userId, object);
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public List<ResourceInfo> uploadFiles(Long userId, String destinationRelativePath, MultipartFile[] files) {

        if (files == null || files.length == 0) {
            throw new StorageException.InvalidPathException(
                    "Не указаны файлы для загрузки",
                    userId,
                    destinationRelativePath,
                    "uploadFiles"
            );
        }

        try {
            List<MinioObject> uploaded = minioServiceAdapter.uploadFiles(userId, destinationRelativePath, files);
            return uploaded.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void deleteResource(Long userId, String relativePath) {

        try {
            // Проверяем существование перед удалением (выбросит исключение если не найдено)
            minioServiceAdapter.getObjectInfo(userId, relativePath);
            minioServiceAdapter.deleteObject(userId, relativePath);
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public ResourceInfo moveResource(Long userId, String fromRelativePath, String toRelativePath) {

        try {
            // Проверяем существование исходного ресурса
            minioServiceAdapter.getObjectInfo(userId, fromRelativePath);

            // Проверяем, не существует ли уже целевой ресурс
            try {
                minioServiceAdapter.getObjectInfo(userId, toRelativePath);
                throw new StorageException.ResourceAlreadyExistsException(
                        "Ресурс уже существует: " + toRelativePath,
                        userId,
                        toRelativePath,
                        "moveResource"
                );
            } catch (StorageException.ResourceNotFoundException ex) {
                // Ресурс не существует - можно продолжать
                // Игнорируем это исключение, так как это ожидаемо
            } catch (Exception e) {
                // Другая ошибка - пробрасываем
                throw e;
            }

            minioServiceAdapter.renameObject(userId, fromRelativePath, toRelativePath);
            return getResourceInfo(userId, toRelativePath);
        } catch (Exception e) {
            throw e;
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

        try {
            List<MinioObject> results = minioServiceAdapter.searchFiles(userId, query);
            return results.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public List<ResourceInfo> getDirectoryContents(Long userId, String relativePath) {
        logger.info("User  {} is requesting contents of directory: '{}'", userId, relativePath);

        try {
            List<MinioObject> objects = minioServiceAdapter.listObjects(userId, relativePath);
            return objects.stream()
                    .map(obj -> convertToResourceInfo(userId, obj))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw e;
        }
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============= 
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
        boolean isDirectory = false;

        if (path.endsWith("/")) {
            isDirectory = true;
            path = path.substring(0, path.length() - 1);
        }

        int lastSlash = path.lastIndexOf('/');
        String nameString = lastSlash != -1 ? path.substring(lastSlash + 1) : path;
        if (isDirectory) {
            return nameString + "/";
        } else {
            return nameString;
        }
    }
}
