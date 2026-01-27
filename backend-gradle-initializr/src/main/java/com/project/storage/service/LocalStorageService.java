package com.project.storage.service;

import com.project.storage.dto.ResourceInfo;
import com.project.storage.model.ResourceType;
import com.project.storage.util.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);

    private final PathValidator pathValidator;
    private final PathResolutionService pathResolutionService;

    public LocalStorageService(
            PathValidator pathValidator,
            PathResolutionService pathResolutionService) {
        this.pathValidator = pathValidator;
        this.pathResolutionService = pathResolutionService;
    }

    @Override
    public ResourceInfo getResourceInfo(String path) throws ResourceNotFoundException {
        logger.debug("Getting resource info for path: {}", path);

        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            logger.error("Invalid path: {}", path, ex);
        }

        Path resourcePath = pathResolutionService.resolveUserPath(path);
        logger.debug("Resolved physical path: {}", resourcePath);

        if (!Files.exists(resourcePath)) {
            String errorMsg = String.format("Resource not found at path: %s (physical: %s)", path, resourcePath);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        try {
            ResourceInfo info = createResourceInfo(path, resourcePath);
            logger.debug("Resource info created: {}", info);
            return info;
        } catch (Exception e) {
            logger.error("Error creating resource info for path: {}", path, e);
            throw new RuntimeException("Failed to get resource info: " + e.getMessage(), e);
        }
    }

    @Override
    public ResourceInfo moveResource(String fromPath, String toPath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException, InvalidPathException {

        logger.debug("Moving resource: {} -> {}", fromPath, toPath);

        // Валидация путей
        validatePath(fromPath);
        validatePath(toPath);

        // Проверка, что типы ресурсов совпадают
        if (!pathValidator.isSameResourceType(fromPath, toPath)) {
            String errorMsg = String.format("Cannot change resource type from %s to %s",
                    pathValidator.validateAndGetType(fromPath),
                    pathValidator.validateAndGetType(toPath));
            logger.warn(errorMsg);
            throw new InvalidPathException(errorMsg);
        }

        Path source = pathResolutionService.resolveUserPath(fromPath);
        Path target = pathResolutionService.resolveUserPath(toPath);

        logger.debug("Source physical path: {}", source);
        logger.debug("Target physical path: {}", target);

        if (!Files.exists(source)) {
            String errorMsg = String.format("Source resource not found: %s", fromPath);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        // Проверяем, не пытаемся ли переместить папку внутрь себя
        if (Files.isDirectory(source) && target.startsWith(source)) {
            String errorMsg = String.format("Cannot move directory inside itself: %s -> %s", fromPath, toPath);
            logger.warn(errorMsg);
            throw new InvalidPathException(errorMsg);
        }

        // Проверяем, существует ли целевой путь (409 по ТЗ)
        if (Files.exists(target)) {
            String errorMsg = String.format("Target resource already exists: %s", toPath);
            logger.warn(errorMsg);
            throw new ResourceAlreadyExistsException(errorMsg);
        }

        // Проверяем существование родительской директории для target
        Path parentDir = target.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            String errorMsg = String.format("Parent directory does not exist: %s",
                    pathValidator.extractParentPath(toPath));
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        try {
            // Создаём родительскую директорию, если нужно
            if (parentDir != null && !Files.exists(parentDir)) {
                logger.debug("Creating parent directory: {}", parentDir);
                Files.createDirectories(parentDir);
            }

            // Перемещаем ресурс
            Files.move(source, target);
            logger.info("Resource moved successfully: {} -> {}", fromPath, toPath);

            // Определяем тип операции
            if (pathValidator.isRenameOnly(fromPath, toPath)) {
                logger.debug("Operation: rename within same directory");
            } else if (pathValidator.isPathChangeOnly(fromPath, toPath)) {
                logger.debug("Operation: move to different directory (same name)");
            } else {
                logger.debug("Operation: rename and move to different directory");
            }

            return createResourceInfo(toPath, target);

        } catch (AccessDeniedException e) {
            String errorMsg = String.format("Access denied when moving resource: %s -> %s", fromPath, toPath);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = String.format("Failed to move resource from %s to %s", fromPath, toPath);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    @Override
    public void deleteResource(String path) throws ResourceNotFoundException {
        logger.debug("Deleting resource: {}", path);

        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            logger.error("Invalid path: {}", path, ex);
            throw new ResourceNotFoundException("Invalid path: " + path);
        }

        Path resourcePath = pathResolutionService.resolveUserPath(path);
        logger.debug("Resolved physical path: {}", resourcePath);

        if (!Files.exists(resourcePath)) {
            String errorMsg = String.format("Resource not found: %s", path);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        try {
            if (Files.isDirectory(resourcePath)) {
                logger.debug("Deleting directory: {}", resourcePath);
                deleteDirectoryRecursively(resourcePath);
            } else {
                logger.debug("Deleting file: {}", resourcePath);
                Files.delete(resourcePath);
            }
            logger.info("Resource deleted successfully: {}", path);
        } catch (IOException e) {
            String errorMsg = String.format("Failed to delete resource: %s", path);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    @Override
    public List<ResourceInfo> searchResources(String query) {
        logger.debug("Searching resources with query: {}", query);

        if (query == null || query.trim().isEmpty()) {
            logger.debug("Empty search query, returning empty list");
            return new ArrayList<>();
        }

        String searchTerm = query.trim().toLowerCase();
        List<ResourceInfo> results = new ArrayList<>();
        Path storageRoot = pathResolutionService.getStorageRoot();

        try (Stream<Path> walk = Files.walk(storageRoot)) {
            walk.forEach(path -> {
                if (!path.equals(storageRoot)
                        && path.getFileName().toString().toLowerCase().contains(searchTerm)) {

                    String relativePath = storageRoot.relativize(path).toString();
                    // Добавляем / для директорий
                    if (Files.isDirectory(path)) {
                        relativePath = relativePath + "/";
                    }

                    results.add(createResourceInfo(relativePath, path));
                }
            });
            logger.debug("Search completed, found {} results for query: {}", results.size(), query);
        } catch (IOException e) {
            String errorMsg = String.format("Failed to search resources with query: %s", query);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }

        return results;
    }

    @Override
    public List<ResourceInfo> uploadFiles(String destinationPath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException {
        logger.debug("Uploading {} files to destination: {}", files.length, destinationPath);
        validatePath(destinationPath);

        Path destDir = pathResolutionService.resolveUserPath(destinationPath);
        logger.debug("Resolved destination directory: {}", destDir);

        if (!Files.isDirectory(destDir)) {
            String errorMsg = String.format("Destination is not a directory: %s", destinationPath);
            logger.warn(errorMsg);
            throw new InvalidPathException(errorMsg);
        }

        List<ResourceInfo> uploadedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            logger.debug("Processing file upload: {}", originalFilename);

            try {
                Path targetPath = destDir.resolve(originalFilename);
                logger.debug("Target path for file: {}", targetPath);

                // Создаём директории, если в имени файла есть путь
                Path parentDir = targetPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    logger.debug("Creating parent directories: {}", parentDir);
                    Files.createDirectories(parentDir);
                }

                // Проверяем, существует ли файл
                if (Files.exists(targetPath)) {
                    String errorMsg = String.format("File already exists: %s", targetPath);
                    logger.warn(errorMsg);
                    throw new ResourceAlreadyExistsException(errorMsg);
                }

                // Сохраняем файл
                logger.debug("Transferring file to: {}", targetPath);
                file.transferTo(targetPath);
                logger.debug("File transferred successfully");

                // Создаём информацию о загруженном файле
                Path storageRoot = pathResolutionService.getStorageRoot();
                String relativePath = storageRoot.relativize(targetPath).toString();
                ResourceInfo info = createResourceInfo(relativePath, targetPath);
                uploadedFiles.add(info);
                logger.debug("File info created: {}", info);

            } catch (IOException e) {
                String errorMsg = String.format("Failed to upload file: %s", originalFilename);
                logger.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
        }

        logger.info("Successfully uploaded {} files to {}", uploadedFiles.size(), destinationPath);
        return uploadedFiles;
    }

    @Override
    public List<ResourceInfo> getDirectoryContents(String path) throws ResourceNotFoundException {
        logger.debug("Getting directory contents for: {}", path);
        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            System.getLogger(LocalStorageService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        Path dirPath = pathResolutionService.resolveUserPath(path);
        logger.debug("Resolved directory path: {}", dirPath);

        if (!Files.exists(dirPath)) {
            String errorMsg = String.format("Directory not found: %s", path);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        if (!Files.isDirectory(dirPath)) {
            String errorMsg = String.format("Path is not a directory: %s", path);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        try (Stream<Path> list = Files.list(dirPath)) {
            List<ResourceInfo> contents = list.map(p -> {
                Path storageRoot = pathResolutionService.getStorageRoot();
                String relativePath = storageRoot.relativize(p).toString();
                // Добавляем / для директорий
                if (Files.isDirectory(p)) {
                    relativePath = relativePath + "/";
                }
                return createResourceInfo(relativePath, p);
            }).collect(Collectors.toList());

            logger.debug("Retrieved {} items from directory: {}", contents.size(), path);
            return contents;
        } catch (IOException e) {
            String errorMsg = String.format("Failed to list directory contents: %s", path);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    @Override
    public ResourceInfo createDirectory(String path)
            throws InvalidPathException, ResourceNotFoundException, ResourceAlreadyExistsException {
        logger.debug("Creating directory: {}", path);
        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            System.getLogger(LocalStorageService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        Path dirPath = pathResolutionService.resolveUserPath(path);
        logger.debug("Resolved directory path: {}", dirPath);

        if (Files.exists(dirPath)) {
            String errorMsg = String.format("Directory already exists: %s", path);
            logger.warn(errorMsg);
            throw new ResourceAlreadyExistsException(errorMsg);
        }

        // Проверяем существование родительской директории
        Path parent = dirPath.getParent();
        logger.debug("Parent directory path: {}", parent);

        if (!Files.exists(parent)) {
            String errorMsg = String.format("Parent directory does not exist for: %s", path);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        if (!Files.isDirectory(parent)) {
            String errorMsg = String.format("Parent path is not a directory for: %s", path);
            logger.warn(errorMsg);
            throw new ResourceNotFoundException(errorMsg);
        }

        try {
            logger.debug("Creating directory: {}", dirPath);
            Files.createDirectory(dirPath);

            ResourceInfo info = createResourceInfo(path, dirPath);
            logger.info("Directory created successfully: {}", info);
            return info;
        } catch (IOException e) {
            String errorMsg = String.format("Failed to create directory: %s", path);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    // Вспомогательные методы
    private void validatePath(String path) throws InvalidPathException {
        logger.debug("Validating path: {}", path);

        if (path == null || path.trim().isEmpty()) {
            String errorMsg = "Path is null or empty";
            logger.warn(errorMsg);
            throw new InvalidPathException(errorMsg);
        }

        ResourceType type = pathValidator.validateAndGetType(path);
        if (type == null) {
            String errorMsg = String.format("Invalid path format: %s. Note: folders must end with '/'", path);
            logger.warn(errorMsg);
            throw new InvalidPathException(errorMsg);
        }

        logger.debug("Path validation passed, type: {}", type);
    }

    private ResourceInfo createResourceInfo(String userPath, Path physicalPath) {
        logger.debug("Creating ResourceInfo for userPath: {}, physicalPath: {}", userPath, physicalPath);

        try {
            String name = pathValidator.extractName(userPath);
            String parentPath = pathValidator.extractParentPath(userPath);
            ResourceType type = Files.isDirectory(physicalPath) ? ResourceType.DIRECTORY : ResourceType.FILE;

            long size = 0L;
            if (type == ResourceType.FILE) {
                try {
                    size = Files.size(physicalPath);
                    logger.debug("File size: {} bytes", size);
                } catch (IOException e) {
                    logger.warn("Failed to get file size for: {}", physicalPath, e);
                }
            }

            ResourceInfo info = new ResourceInfo(parentPath, name, size, type);
            logger.debug("Created ResourceInfo: {}", info);
            return info;
        } catch (Exception e) {
            logger.error("Error creating ResourceInfo for path: {}", userPath, e);
            throw new RuntimeException("Failed to create resource info: " + e.getMessage(), e);
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        logger.debug("Deleting directory recursively: {}", dir);
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> -a.compareTo(b)) // удаляем сначала содержимое
                    .forEach(p -> {
                        try {
                            logger.trace("Deleting: {}", p);
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
        }
    }
}
