package com.project.storage.service;

import com.project.service.MinioService;
import com.project.service.StorageService;
import com.project.exception.StorageException;
import com.project.storage.dto.ResourceInfo; 
import com.project.storage.util.PathValidator;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

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
@RequiredArgsConstructor
public class MinioDownloadService implements DownloadService {

    private static final Logger logger = LoggerFactory.getLogger(MinioDownloadService.class);

    private final PathValidator pathValidator;
    private final StorageException StorageException;
    private final MinioService minioService;
    private final MinioClient minioClient;

    @Override
    public DownloadResult getDownloadResource(Long userId, String path)
            throws StorageException.ResourceNotFoundException,
            StorageException.InvalidPathException,
            IOException {

        logger.info("User {}: Preparing download for path: {}", userId, path);

        // Валидация пути
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageException.InvalidPathException("Invalid path: " + path);
        }

        // Проверяем существование ресурса через StorageException
        ResourceInfo resourceInfo = StorageService.getResourceInfo(userId, path);

        // Определяем полный путь в MinIO
        String userFolder = "user-" + userId + "-files";
        String fullPath = path.equals("/")
                ? userFolder + "/"
                : userFolder + "/" + path;

        // Проверяем существование объекта в MinIO
        if (!minioService.objectExists(fullPath)) {
            throw new StorageException.ResourceNotFoundException("Resource not found: " + path);
        }

        // Обработка файла
        if (resourceInfo.getType() == com.project.storage.model.ResourceType.FILE) {
            logger.debug("User {}: Downloading file: {}", userId, path);
            return downloadFileFromMinio(fullPath, resourceInfo.getName());
        } // Обработка папки
        else {
            logger.debug("User {}: Downloading directory as zip: {}", userId, path);
            return downloadDirectoryAsZip(userId, fullPath, resourceInfo.getName());
        }
    }

    /**
     * Скачивание файла из MinIO
     */
    private DownloadResult downloadFileFromMinio(String fullPath, String originalName)
            throws IOException {

        try {
            // Используем minioClient напрямую
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioService.getBucketName())
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
    private DownloadResult downloadDirectoryAsZip(Long userId, String folderPath, String folderName)
            throws IOException {

        Path tempZip = null;
        try {
            // Получаем все файлы в папке рекурсивно
            List<MinioFileInfo> files = getAllFilesInFolder(userId, folderPath);

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
            logger.error("Error creating zip from MinIO folder: {}", folderPath, e);

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
    private List<MinioFileInfo> getAllFilesInFolder(Long userId, String folderPath) {
        List<MinioFileInfo> files = new ArrayList<>();

        try {
            // Получаем содержимое папки через StorageService
            String relativePath = folderPath.replace("user-" + userId + "-files/", "");
            if (relativePath.endsWith("/")) {
                relativePath = relativePath.substring(0, relativePath.length() - 1);
            }

            List<ResourceInfo> contents = StorageService.getDirectoryContents(userId, relativePath);

            for (ResourceInfo item : contents) {
                if (item.getType() == com.project.storage.model.ResourceType.FILE) {
                    // Для файлов
                    String fullItemPath = folderPath + "/" + item.getName();
                    files.add(new MinioFileInfo(
                            fullItemPath,
                            item.getName(),
                            item.getSize()
                    ));
                } else {
                    // Для подпапок - рекурсивно получаем содержимое
                    String subFolderPath = folderPath + "/" + item.getName();
                    files.addAll(getAllFilesInFolder(userId, subFolderPath));
                }
            }

        } catch (Exception e) {
            logger.error("Error getting files in folder: {}", folderPath, e);
        }

        return files;
    }

    /**
     * Добавление файла из MinIO в ZIP архив
     */
    private void addFileToZip(ZipOutputStream zos, MinioFileInfo fileInfo) throws IOException {
        try (InputStream fileStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioService.getBucketName())
                        .object(fileInfo.getFullPath())
                        .build()
        )) {
            // Определяем путь внутри ZIP архива
            String zipEntryName = extractZipEntryName(fileInfo.getFullPath());
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
            logger.error("Error adding file to zip: {}", fileInfo.getFullPath(), e);
            throw new IOException("Failed to add file to zip: " + fileInfo.getFullPath(), e);
        }
    }

    /**
     * Извлечение имени для записи в ZIP (убираем префикс папки пользователя)
     */
    private String extractZipEntryName(String fullPath) {
        // Ищем "user-{id}-files/" в пути
        int userFolderIndex = fullPath.indexOf("user-");
        if (userFolderIndex != -1) {
            // Находим конец папки пользователя
            int endOfUserFolder = fullPath.indexOf("-files/", userFolderIndex);
            if (endOfUserFolder != -1) {
                // Берем часть после папки пользователя
                return fullPath.substring(endOfUserFolder + 7); // +7 для пропуска "-files/"
            }
        }
        return fullPath;
    }

    /**
     * Внутренний класс для хранения информации о файле в MinIO
     */
    private static class MinioFileInfo {

        private final String fullPath;
        private final String name;
        private final long size;

        public MinioFileInfo(String fullPath, String name, long size) {
            this.fullPath = fullPath;
            this.name = name;
            this.size = size;
        }

        public String getFullPath() {
            return fullPath;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }
    }

    /**
     * Альтернативный метод для скачивания через прямую ссылку (без
     * промежуточного ZIP)
     */
    public String getDirectDownloadUrl(Long userId, String path) {
        try {
            String userFolder = "user-" + userId + "-files";
            String fullPath = path.equals("/")
                    ? userFolder + "/"
                    : userFolder + "/" + path;

            // Генерируем пре-подписанную URL для прямого скачивания
            return minioService.getDownloadUrl(fullPath);

        } catch (Exception e) {
            logger.error("Error generating direct download URL for user {} path {}: {}",
                    userId, path, e.getMessage());
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }
}
