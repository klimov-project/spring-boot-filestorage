package com.project.storage.service;

import com.project.storage.dto.ResourceInfo;
import com.project.storage.model.ResourceType;
import com.project.storage.util.PathValidator;

import org.springframework.beans.factory.annotation.Value;
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

    private final PathValidator pathValidator;
    private final Path storageRoot;

    public LocalStorageService(
            PathValidator pathValidator,
            @Value("${storage.root.path:./storage}") String storageRootPath) {
        this.pathValidator = pathValidator;
        this.storageRoot = Paths.get(storageRootPath).toAbsolutePath().normalize();

        // Создаём корневую директорию, если её нет
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory", e);
        }
    }

    @Override
    public ResourceInfo getResourceInfo(String path) throws ResourceNotFoundException {
        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            System.getLogger(LocalStorageService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        Path resourcePath = resolveUserPath(path);
        if (!Files.exists(resourcePath)) {
            throw new ResourceNotFoundException("Resource not found: " + path);
        }

        return createResourceInfo(path, resourcePath);
    }

    @Override
    public void deleteResource(String path) throws ResourceNotFoundException {
        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            System.getLogger(LocalStorageService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        Path resourcePath = resolveUserPath(path);
        if (!Files.exists(resourcePath)) {
            throw new ResourceNotFoundException("Resource not found: " + path);
        }

        try {
            if (Files.isDirectory(resourcePath)) {
                deleteDirectoryRecursively(resourcePath);
            } else {
                Files.delete(resourcePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete resource: " + path, e);
        }
    }

    @Override
    public ResourceInfo moveResource(String fromPath, String toPath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException {

        try {
            validatePath(fromPath);
            validatePath(toPath);
        } catch (InvalidPathException ex) {
            System.getLogger(LocalStorageService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        Path source = resolveUserPath(fromPath);
        Path target = resolveUserPath(toPath);

        if (!Files.exists(source)) {
            throw new ResourceNotFoundException("Source resource not found: " + fromPath);
        }

        if (Files.exists(target)) {
            throw new ResourceAlreadyExistsException("Target resource already exists: " + toPath);
        }

        try {
            // Создаём родительскую директорию, если нужно
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

            return createResourceInfo(toPath, target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to move resource", e);
        }
    }

    @Override
    public List<ResourceInfo> searchResources(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchTerm = query.trim().toLowerCase();
        List<ResourceInfo> results = new ArrayList<>();

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
        } catch (IOException e) {
            throw new RuntimeException("Failed to search resources", e);
        }

        return results;
    }

    @Override
    public List<ResourceInfo> uploadFiles(String destinationPath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException {
        validatePath(destinationPath);

        Path destDir = resolveUserPath(destinationPath);
        if (!Files.isDirectory(destDir)) {
            throw new InvalidPathException("Destination is not a directory: " + destinationPath);
        }

        List<ResourceInfo> uploadedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String originalFilename = file.getOriginalFilename();
                Path targetPath = destDir.resolve(originalFilename);

                // Создаём директории, если в имени файла есть путь
                Files.createDirectories(targetPath.getParent());

                // Проверяем, существует ли файл
                if (Files.exists(targetPath)) {
                    throw new ResourceAlreadyExistsException("File already exists: " + originalFilename);
                }

                // Сохраняем файл
                file.transferTo(targetPath);

                // Создаём информацию о загруженном файле
                String relativePath = storageRoot.relativize(targetPath).toString();
                uploadedFiles.add(createResourceInfo(relativePath, targetPath));

            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file: " + file.getOriginalFilename(), e);
            }
        }

        return uploadedFiles;
    }

    @Override
    public List<ResourceInfo> getDirectoryContents(String path) throws ResourceNotFoundException {
        try {
            validatePath(path);
        } catch (InvalidPathException ex) {
            System.getLogger(LocalStorageService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        Path dirPath = resolveUserPath(path);
        if (!Files.exists(dirPath)) {
            throw new ResourceNotFoundException("Directory not found: " + path);
        }

        if (!Files.isDirectory(dirPath)) {
            throw new ResourceNotFoundException("Path is not a directory: " + path);
        }

        try (Stream<Path> list = Files.list(dirPath)) {
            return list.map(p -> {
                String relativePath = storageRoot.relativize(p).toString();
                // Добавляем / для директорий
                if (Files.isDirectory(p)) {
                    relativePath = relativePath + "/";
                }
                return createResourceInfo(relativePath, p);
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to list directory contents: " + path, e);
        }
    }

    @Override
    public ResourceInfo createDirectory(String path)
            throws InvalidPathException, ResourceNotFoundException, ResourceAlreadyExistsException {
        validatePath(path);

        Path dirPath = resolveUserPath(path);
        if (Files.exists(dirPath)) {
            throw new ResourceAlreadyExistsException("Directory already exists: " + path);
        }

        // Проверяем существование родительской директории
        Path parent = dirPath.getParent();
        if (!Files.exists(parent)) {
            throw new ResourceNotFoundException("Parent directory does not exist for: " + path);
        }

        try {
            Files.createDirectory(dirPath);
            return createResourceInfo(path, dirPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }

    // Вспомогательные методы
    private void validatePath(String path) throws InvalidPathException {
        ResourceType type = pathValidator.validateAndGetType(path);
        if (type == null) {
            throw new InvalidPathException("Invalid path: " + path);
        }
    }

    private Path resolveUserPath(String userPath) {
        Path resolved = storageRoot.resolve(userPath).normalize();

        // Защита от path traversal за пределы storageRoot
        if (!resolved.startsWith(storageRoot)) {
            throw new SecurityException("Access denied: path traversal attempt");
        }

        return resolved;
    }

    private ResourceInfo createResourceInfo(String userPath, Path physicalPath) {
        String name = pathValidator.extractName(userPath);
        String parentPath = pathValidator.extractParentPath(userPath);
        ResourceType type = Files.isDirectory(physicalPath) ? ResourceType.DIRECTORY : ResourceType.FILE;

        ResourceInfo info = new ResourceInfo();
        info.setPath(parentPath);
        info.setName(name);
        info.setType(type);

        if (type == ResourceType.FILE) {
            try {
                info.setSize(Files.size(physicalPath));
            } catch (IOException e) {
                info.setSize(0L);
            }
        }

        return info;
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> -a.compareTo(b)) // удаляем сначала содержимое
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
        }
    }
}
