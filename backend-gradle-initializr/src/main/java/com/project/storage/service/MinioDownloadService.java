package com.project.storage.service;

import com.project.exception.StorageException;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.util.PathValidator;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MinioDownloadService implements DownloadService {

    private static final Logger logger = LoggerFactory.getLogger(MinioDownloadService.class);

    @Value("${spring.minio.bucket}")
    private String bucket;
    private final StorageService storageService;
    private final MinioClient minioClient;
    private final MinioServiceAdapter minioServiceAdapter;
    private final PathValidator pathValidator;

    public MinioDownloadService(
            StorageService storageService,
            MinioClient minioClient,
            MinioServiceAdapter minioServiceAdapter,
            PathValidator pathValidator) {
        this.storageService = storageService;
        this.minioClient = minioClient;
        this.minioServiceAdapter = minioServiceAdapter;
        this.pathValidator = pathValidator;
    }

    @Override
    public DownloadResult getDownloadResource(Long userId, String path) throws IOException {
        logger.info("User {}: Preparing download for path: {}", userId, path);

        // Валидация пути
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageException.InvalidPathException(
                    "Invalid path: " + path,
                    userId,
                    path,
                    "downloadResource"
            );
        }

        // Проверяем существование ресурса через StorageService
        ResourceInfo resourceInfo = storageService.getResourceInfo(userId, path);

        // Проверяем существование объекта в MinIO через адаптер
        if (!minioServiceAdapter.objectExists(userId, path)) {
            throw new StorageException.ResourceNotFoundException(
                    "Resource not found: " + path,
                    userId,
                    path,
                    "downloadResource"
            );
        }

        // Обработка файла
        if (resourceInfo.getType() == com.project.storage.model.ResourceType.FILE) {
            logger.debug("User {}: Downloading file: {}", userId, path);
            return downloadFileFromMinio(userId, path, resourceInfo.getName());
        } // Обработка папки
        else {
            logger.debug("User {}: Downloading directory as zip: {}", userId, path);
            return downloadDirectoryAsZip(userId, path, resourceInfo.getName());
        }
    }

    /**
     * Скачивание файла из MinIO
     */
    private DownloadResult downloadFileFromMinio(Long userId, String relativePath, String originalName)
            throws IOException {

        // Получаем полный путь через адаптер
        String fullPath = getFullPathForMinio(userId, relativePath);

        try {
            // Используем minioClient напрямую для скачивания
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );

            // Создаем ресурс из InputStream
            Resource resource = new InputStreamResource(stream) {
                @Override
                public String getFilename() {
                    return originalName;
                }
            };

            return new DownloadResult(resource, originalName, false);

        } catch (Exception e) {
            logger.error("Error downloading file from MinIO: {}", fullPath, e);
            throw new IOException("Failed to download file from MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Скачивание папки как ZIP архива из MinIO
     */
    private DownloadResult downloadDirectoryAsZip(Long userId, String relativePath, String folderName)
            throws IOException {

        Path tempZip = null;
        try {
            // Получаем все файлы в папке рекурсивно
            List<MinioFileInfo> files = getAllFilesInFolder(userId, relativePath);

            if (files.isEmpty()) {
                // Создаем пустой ZIP для пустой папки
                tempZip = Files.createTempFile(folderName + "_" + UUID.randomUUID(), ".zip");
                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                    // Пустой ZIP
                }
            } else {
                // Создаем временный файл для ZIP архива
                tempZip = Files.createTempFile(folderName + "_" + UUID.randomUUID(), ".zip");

                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                    for (MinioFileInfo fileInfo : files) {
                        addFileToZip(zos, fileInfo);
                    }
                }
            }

            // Создаем InputStream для временного файла
            InputStream zipStream = Files.newInputStream(tempZip);
            Path finalTempZip = tempZip; // для использования в лямбде

            // Создаем ресурс для временного файла
            Resource resource = new InputStreamResource(zipStream) {
                @Override
                public String getFilename() {
                    return folderName + ".zip";
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return Files.newInputStream(finalTempZip);
                }

                @Override
                public boolean isOpen() {
                    return true;
                }
            };

            String zipFilename = folderName + ".zip";
            return new DownloadResult(resource, zipFilename, true);

        } catch (Exception e) {
            logger.error("Error creating zip from MinIO folder: {}", relativePath, e);

            // Очистка временного файла в случае ошибки
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ex) {
                    logger.warn("Failed to delete temp file: {}", tempZip, ex);
                }
            }

            throw new IOException("Failed to create zip archive: " + e.getMessage(), e);
        }
    }

    /**
     * Получение всех файлов в папке (рекурсивно)
     */
    private List<MinioFileInfo> getAllFilesInFolder(Long userId, String relativePath) {
        List<MinioFileInfo> files = new ArrayList<>();

        try {
            List<ResourceInfo> contents = storageService.getDirectoryContents(userId, relativePath);

            for (ResourceInfo item : contents) {
                if (item.getType() == com.project.storage.model.ResourceType.FILE) {
                    // Для файлов
                    files.add(new MinioFileInfo(
                            userId,
                            relativePath + "/" + item.getName(),
                            item.getName(),
                            item.getSize()
                    ));
                } else {
                    // Для подпапок - рекурсивно получаем содержимое
                    String subFolderPath = relativePath + "/" + item.getName();
                    files.addAll(getAllFilesInFolder(userId, subFolderPath));
                }
            }

        } catch (Exception e) {
            logger.error("Error getting files in folder: {}", relativePath, e);
        }

        return files;
    }

    /**
     * Добавление файла из MinIO в ZIP архив
     */
    private void addFileToZip(ZipOutputStream zos, MinioFileInfo fileInfo) throws IOException {
        String fullPath = getFullPathForMinio(fileInfo.getUserId(), fileInfo.getRelativePath());

        try (InputStream fileStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(fullPath)
                        .build()
        )) {
            // Определяем путь внутри ZIP архива
            String zipEntryName = extractZipEntryName(fileInfo.getRelativePath());
            ZipEntry zipEntry = new ZipEntry(zipEntryName);
            zos.putNextEntry(zipEntry);

            // Копируем содержимое файла в ZIP
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }

            zos.closeEntry();
        } catch (Exception e) {
            logger.error("Error adding file to zip: {}", fileInfo.getRelativePath(), e);
            throw new IOException("Failed to add file to zip: " + fileInfo.getRelativePath(), e);
        }
    }

    /**
     * Извлечение имени для записи в ZIP (убираем префикс папки пользователя)
     */
    private String extractZipEntryName(String relativePath) {
        // Убираем ведущий слеш если есть
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return relativePath;
    }

    /**
     * Получение полного пути для MinIO
     */
    private String getFullPathForMinio(Long userId, String relativePath) {
        return "user-" + userId + "-files/"
                + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
    }

    /**
     * Внутренний класс для хранения информации о файле в MinIO
     */
    private static class MinioFileInfo {

        private final Long userId;
        private final String relativePath;
        private final String name;
        private final long size;

        public MinioFileInfo(Long userId, String relativePath, String name, long size) {
            this.userId = userId;
            this.relativePath = relativePath;
            this.name = name;
            this.size = size;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }
    }

    @Override
    public String getDirectDownloadUrl(Long userId, String path) {
        try {
            return minioServiceAdapter.getDownloadUrl(userId, path);
        } catch (Exception e) {
            logger.error("Error generating direct download URL for user {} path {}: {}",
                    userId, path, e.getMessage());
            throw e;
        }

    }
}
