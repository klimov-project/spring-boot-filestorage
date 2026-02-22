package com.project.storage.service;

import com.project.entity.MinioObject;
import com.project.exception.StorageException;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.util.PathValidator;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.project.storage.model.ResourceType;

import java.io.ByteArrayOutputStream;
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
    public DownloadResult getDownloadResource(Long userId, String path) {
        logger.info("User {}: Preparing download for path: {}", userId, path);

        try {
            // 1. Валидация пути (тип не известен заранее, поэтому expectedType = null)
            pathValidator.assertValidPathOrThrow(path, null, userId, "getDownloadResource");

            // 2. Проверяем существование объекта
            if (!minioServiceAdapter.isObjectExists(userId, path)) {
                throw new StorageException.ResourceNotFoundException(
                        "Ресурс не найден: " + path,
                        userId,
                        path,
                        "getDownloadResource"
                );
            }

            // 3. Получаем информацию о ресурсе через адаптер
            MinioObject objectInfo = minioServiceAdapter.getObjectInfo(userId, path);

            // 4. Определяем тип ресурса
            boolean isDirectory = objectInfo.isDirectory() || path.endsWith("/");

            // 5. Повторная строгая сверка типа (на случай рассинхрона с Minio)
            ResourceType requestedType = pathValidator.validateAndGetType(path);
            if (isDirectory && requestedType == ResourceType.FILE) {
                throw new StorageException.InvalidPathException(
                        "Ожидался файл, но найден каталог: " + path,
                        userId,
                        path,
                        "getDownloadResource"
                );
            } else if (!isDirectory && requestedType == ResourceType.DIRECTORY) {
                throw new StorageException.InvalidPathException(
                        "Ожидался каталог, но найден файл: " + path,
                        userId,
                        path,
                        "getDownloadResource"
                );
            }

            // 6. Скачивание
            if (!isDirectory) {
                logger.debug("User {}: Downloading file: {}", userId, path);
                return downloadFileFromMinio(userId, path, objectInfo.getName());
            } else {
                logger.debug("User {}: Downloading directory as zip: {}", userId, path);
                return downloadDirectoryAsZip(userId, path, objectInfo.getName());
            }
        } catch (Exception e) {
            logger.error("Unexpected error for user {}: {}", userId, e.getMessage(), e);
            throw new StorageException.InvalidPathException(
                    "Невалидный или отсутствующий путь: " + path,
                    userId,
                    path,
                    "getDownloadResource"
            );
        }
    }

    /**
     * Скачивание файла из MinIO
     */
    private DownloadResult downloadFileFromMinio(Long userId, String relativePath, String originalName)
            throws Exception {

        String fullPath = getFullPathForMinio(userId, relativePath);

        try {
            // Читаем весь файл в память
            byte[] fileContent;
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            )) {
                fileContent = readAllBytes(stream);
            }

            // Используем ByteArrayResource вместо InputStreamResource
            Resource resource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return originalName;
                }
            };

            return new DownloadResult(resource, originalName, false);

        } catch (Exception e) {
            logger.error("Error downloading file from MinIO: {}", fullPath, e);
            throw new StorageException.StorageOperationException(
                    "Ошибка скачивания файла: " + e.getMessage(),
                    userId,
                    relativePath,
                    "downloadFile"
            );
        }
    }

    /**
     * Скачивание папки как ZIP архива из MinIO
     */
    private DownloadResult downloadDirectoryAsZip(Long userId, String relativePath, String folderName)
            throws Exception {

        Path tempZip = null;
        try {
            // Получаем все файлы в папке рекурсивно
            List<MinioFileInfo> files = getAllFilesInFolder(userId, relativePath);

            // Создаем временный файл для ZIP архива
            tempZip = Files.createTempFile(folderName + "_" + UUID.randomUUID(), ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                for (MinioFileInfo fileInfo : files) {
                    addFileToZip(zos, fileInfo);
                }
            }

            // Читаем ZIP файл в память
            byte[] zipContent = Files.readAllBytes(tempZip);
            String zipFilename = folderName + ".zip";

            // Удаляем временный файл сразу после чтения
            Files.deleteIfExists(tempZip);

            // Используем ByteArrayResource
            Resource resource = new ByteArrayResource(zipContent) {
                @Override
                public String getFilename() {
                    return zipFilename;
                }
            };

            return new DownloadResult(resource, zipFilename, true);

        } catch (Exception e) {
            // Очистка временного файла в случае ошибки
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (Exception ex) {
                    logger.warn("Failed to delete temp file: {}", tempZip, ex);
                }
            }

            logger.error("Error creating zip from MinIO folder: {}", relativePath, e);
            throw new StorageException.StorageOperationException(
                    "Ошибка создания ZIP архива: " + e.getMessage(),
                    userId,
                    relativePath,
                    "createZip"
            );
        }
    }

    /**
     * Чтение всех байт из InputStream
     */
    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Получение всех файлов в папке (рекурсивно)
     */
    private List<MinioFileInfo> getAllFilesInFolder(Long userId, String relativePath) {
        List<MinioFileInfo> files = new ArrayList<>();

        try {

            List<ResourceInfo> contents = storageService.getDirectoryContents(userId, relativePath);

            for (ResourceInfo item : contents) {
                String itemPath = relativePath + (relativePath.endsWith("/") ? "" : "/") + item.getName();

                if (item.getType() == ResourceType.FILE) {
                    files.add(new MinioFileInfo(
                            userId,
                            itemPath,
                            item.getName(),
                            item.getSize()
                    ));
                } else {
                    // Рекурсивно для подпапок
                    files.addAll(getAllFilesInFolder(userId, itemPath));
                }
            }
        } catch (Exception e) {
            logger.error("Error getting files in folder: {}", relativePath, e);
            throw new StorageException.StorageOperationException(
                    "Ошибка получения списка файлов: " + e.getMessage(),
                    userId,
                    relativePath,
                    "listFiles"
            );
        }

        return files;
    }

    /**
     * Добавление файла из MinIO в ZIP архив
     */
    private void addFileToZip(ZipOutputStream zos, MinioFileInfo fileInfo) throws Exception {
        String fullPath = getFullPathForMinio(fileInfo.getUserId(), fileInfo.getRelativePath());

        try (InputStream fileStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(fullPath)
                        .build()
        )) {
            String zipEntryName = extractZipEntryName(fileInfo.getRelativePath());
            ZipEntry zipEntry = new ZipEntry(zipEntryName);
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }

            zos.closeEntry();
        } catch (Exception e) {
            logger.error("Error adding file to zip: {}", fileInfo.getRelativePath(), e);
            throw new RuntimeException("Failed to add file to zip: " + fileInfo.getRelativePath(), e);
        }
    }

    /**
     * Извлечение имени для записи в ZIP
     */
    private String extractZipEntryName(String relativePath) {
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return relativePath;
    }

    /**
     * Получение полного пути для MinIO
     */
    private String getFullPathForMinio(Long userId, String relativePath) {
        String userPrefix = "user-" + userId + "-files/";
        String cleanPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return userPrefix + cleanPath;
    }

    @Override
    public String getDirectDownloadUrl(Long userId, String path) {
        try {
            return minioServiceAdapter.getDownloadUrl(userId, path);
        } catch (Exception e) {
            logger.error("Unexpected error generating download URL", e);
            throw new StorageException.StorageOperationException(
                    "Ошибка генерации ссылки: " + e.getMessage(),
                    userId,
                    path,
                    "getDirectDownloadUrl"
            );
        }
    }

    /**
     * Внутренний класс для хранения информации о файле
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

}
