package com.project.storage.service;

import com.project.storage.util.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LocalDownloadService implements DownloadService {

    private static final Logger logger = LoggerFactory.getLogger(LocalDownloadService.class);

    private final PathValidator pathValidator;
    private final Path storageRoot;
    private final StorageService storageService;

    public LocalDownloadService(
            PathValidator pathValidator,
            @Value("${storage.root.path:./storage}") String storageRootPath,
            StorageService storageService) {
        this.pathValidator = pathValidator;
        this.storageRoot = Paths.get(storageRootPath).toAbsolutePath().normalize();
        this.storageService = storageService;
        logger.info("LocalDownloadService initialized with root: {}", storageRoot);
    }

    @Override
    public DownloadResult getDownloadResource(String path)
            throws StorageService.ResourceNotFoundException,
            StorageService.InvalidPathException,
            IOException {

        logger.info("Preparing download for path: {}", path);

        // Валидация пути
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageService.InvalidPathException("Invalid path: " + path);
        }

        // Проверяем существование ресурса через StorageService
        var resourceInfo = storageService.getResourceInfo(path);

        // Разрешаем физический путь
        Path physicalPath = resolveUserPath(path);

        if (!Files.exists(physicalPath)) {
            throw new StorageService.ResourceNotFoundException("Resource not found: " + path);
        }

        // Обработка файла
        if (resourceInfo.getType() == com.project.storage.model.ResourceType.FILE) {
            logger.debug("Downloading file: {}", path);
            return downloadFile(physicalPath, resourceInfo.getName());
        } // Обработка папки
        else {
            logger.debug("Downloading directory as zip: {}", path);
            return downloadDirectoryAsZip(physicalPath, resourceInfo.getName());
        }
    }

    private DownloadResult downloadFile(Path filePath, String originalName) throws IOException {
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("File is not readable: " + filePath);
        }

        return new DownloadResult(resource, originalName, false);
    }

    private DownloadResult downloadDirectoryAsZip(Path dirPath, String dirName) throws IOException {
        // Создаем временный zip файл
        Path tempZip = Files.createTempFile(dirName + "_", ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
            zipDirectory(dirPath, dirPath.getFileName().toString(), zos);
        }

        Resource resource = new UrlResource(tempZip.toUri());

        // Удаляем временный файл после завершения скачивания
        // В реальном приложении можно использовать Resource с callback для удаления
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException e) {
                logger.warn("Failed to delete temp zip file: {}", tempZip, e);
            }
        }));

        String zipFilename = dirName + ".zip";
        return new DownloadResult(resource, zipFilename, true);
    }

    private void zipDirectory(Path sourceDir, String baseDir, ZipOutputStream zos) throws IOException {
        try (var walk = Files.walk(sourceDir)) {
            walk.filter(path -> !Files.isDirectory(path))
                    .forEach(file -> {
                        try {
                            String zipPath = baseDir + "/" + sourceDir.relativize(file).toString();
                            ZipEntry zipEntry = new ZipEntry(zipPath);
                            zos.putNextEntry(zipEntry);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to add file to zip: " + file, e);
                        }
                    });
        }
    }

    private Path resolveUserPath(String userPath) {
        Path resolved = storageRoot.resolve(userPath).normalize();

        // Защита от path traversal
        if (!resolved.startsWith(storageRoot)) {
            throw new SecurityException("Access denied: path traversal attempt");
        }

        return resolved;
    }
}
