package com.project.storage.service;

import com.project.entity.MinioObject;
import com.project.service.MinioService;
import com.project.storage.dto.ResourceInfo;
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

    /**
     * Создание корневой директории пользователя
     *
     * @Override
     */
    @Override
    public void createUserDirectory(Long userId) {
        String userRootFolder = "user-" + userId + "-files/";
        try {
            minioService.createFolder(userRootFolder);
        } catch (Exception e) {
            // Преобразуем исключение MinIO в наше
            throw new RuntimeException("Failed to create user directory", e);
        }
    }

    /**
     * Получение полного пути в MinIO для пользователя
     */
    private String getFullPath(Long userId, String relativePath) {
        String userFolder = getUserFolderPath(userId);

        if (relativePath == null || relativePath.isEmpty() || "/".equals(relativePath)) {
            return userFolder + "/";
        }

        // Убираем начальный слэш если есть
        String cleanPath = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;

        // Добавляем слэш в конце если это папка и его нет
        if (cleanPath.endsWith("/")) {
            return userFolder + "/" + cleanPath;
        }

        return userFolder + "/" + cleanPath;
    }

    /**
     * Получение относительного пути (без префикса пользователя)
     */
    private String getRelativePath(Long userId, String fullPath) {
        String userPrefix = getUserFolderPath(userId) + "/";

        if (fullPath.startsWith(userPrefix)) {
            return fullPath.substring(userPrefix.length());
        }

        // Если путь не содержит префикс пользователя, возвращаем как есть
        return fullPath;
    }

    /**
     * Получение пути к папке пользователя
     */
    public String getUserFolderPath(Long userId) {
        return String.format("user-%d-files", userId);
    }

    /**
     * Валидация пути для предотвращения path traversal атак
     */
    private void validatePath(String path) {
        if (path == null) {
            return;
        }

        if (path.contains("..")) {
            throw new IllegalArgumentException("Path traversal not allowed");
        }

        if (path.contains("//")) {
            throw new IllegalArgumentException("Invalid path format");
        }
    }

    @Override
    public ResourceInfo getResourceInfo(Long userId, String relativePath)
            throws ResourceNotFoundException {

        validatePath(relativePath);
        String fullPath = getFullPath(userId, relativePath);

        if ("/".equals(relativePath)) {
            // Возвращаем специальный объект для корневой директории
            return ResourceInfo.builder()
                    .path("/")
                    .name("root")
                    .type(ResourceType.DIRECTORY)
                    .userId(userId)
                    .build();
        }

        List<MinioObject> objects = minioService.listObjects(fullPath);
        if (objects.isEmpty()) {
            throw new ResourceNotFoundException("Resource not found: " + relativePath);
        }
        return convertToResourceInfo(userId, objects.get(0));
    }

    @Override
    public void deleteResource(Long userId, String relativePath)
            throws ResourceNotFoundException {

        validatePath(relativePath);
        String fullPath = getFullPath(userId, relativePath);
        minioService.deleteObject(fullPath);
    }

    @Override
    public ResourceInfo moveResource(Long userId, String fromRelativePath, String toRelativePath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException, InvalidPathException {

        validatePath(fromRelativePath);
        validatePath(toRelativePath);

        String fromFullPath = getFullPath(userId, fromRelativePath);
        String toFullPath = getFullPath(userId, toRelativePath);

        minioService.renameObject(fromFullPath, toFullPath);
        return getResourceInfo(userId, toRelativePath);
    }

    @Override
    public List<ResourceInfo> searchResources(Long userId, String query) {
        String userFolder = getUserFolderPath(userId);
        return minioService.searchFiles(userFolder, query).stream()
                .map(obj -> convertToResourceInfo(userId, obj))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceInfo> uploadFiles(Long userId, String destinationRelativePath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException {

        validatePath(destinationRelativePath);
        String destinationFullPath = getFullPath(userId, destinationRelativePath);

        return minioService.uploadFiles(destinationFullPath, files).stream()
                .map(obj -> convertToResourceInfo(userId, obj))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceInfo> getDirectoryContents(Long userId, String relativePath)
            throws ResourceNotFoundException {

        validatePath(relativePath);
        String fullPath = getFullPath(userId, relativePath);

        return minioService.listObjects(fullPath).stream()
                .map(obj -> convertToResourceInfo(userId, obj))
                .collect(Collectors.toList());
    }

    @Override
    public ResourceInfo createDirectory(Long userId, String relativePath)
            throws InvalidPathException, ResourceNotFoundException, ResourceAlreadyExistsException {

        validatePath(relativePath);
        String fullPath = getFullPath(userId, relativePath);

        // Добавляем слэш в конце если это папка
        if (!fullPath.endsWith("/")) {
            fullPath = fullPath + "/";
        }

        minioService.createFolder(fullPath);
        return getResourceInfo(userId, relativePath);
    }

    /**
     * Конвертация MinioObject в ResourceInfo с учетом userId
     */
    private ResourceInfo convertToResourceInfo(Long userId, MinioObject minioObject) {
        String relativePath = getRelativePath(userId, minioObject.getPath());
        String name = extractNameFromPath(relativePath);

        ResourceType type = minioObject.isDirectory() ? ResourceType.DIRECTORY : ResourceType.FILE;

        return ResourceInfo.builder()
                .path(relativePath)
                .name(name)
                .size(type == ResourceType.FILE ? minioObject.getSize() : null)
                .type(type)
                .userId(userId)
                .build();
    }

    /**
     * Извлечение имени файла/папки из пути
     */
    private String extractNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            return path.substring(lastSlash + 1);
        }

        return path;
    }
}
