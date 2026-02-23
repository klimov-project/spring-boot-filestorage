package com.project.storage.service;

import com.project.entity.MinioObject;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class MinioServiceImpl implements MinioService {

    private static final Logger logger = LoggerFactory.getLogger(MinioServiceImpl.class);

    @Value("${spring.minio.bucket}")
    private String bucket;

    private final MinioClient minioClient;

    @Override
    public List<MinioObject> listObjects(String fullPath) {
        try {
            String prefix = ensureTrailingSlash(fullPath);
            List<MinioObject> objects = new ArrayList<>();

            for (Result<Item> result : minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(false)
                            .build()
            )) {
                Item item = result.get();
                // Пропускаем саму папку (объект с именем равным префиксу)
                if (item.objectName().equals(prefix)) {
                    continue;
                }

                objects.add(createMinioObject(item));
            }

            return objects;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Создаёт папку в Minio.
     *
     * @param userId id пользователя
     * @param fullPath полный путь к папке (с / на конце)
     * @param strict true — строгий режим (ошибка, если уже есть файл/папка с
     * таким именем), false — нестрогий (ошибки игнорируются)
     */
    // По умолчанию — строгий режим 
    @Override
    public void createFolder(String fullPath) {
        createFolder(fullPath, true);
    }

    @Override
    public void createFolder(String fullPath, boolean strict) {
        try {
            boolean exists = isObjectExists(fullPath);

            if (exists) {
                if (strict) {
                    throw new RuntimeException("Folder already exists: " + fullPath);
                } else {
                    logger.debug("Folder already exists (non-strict mode): {}", fullPath);
                    return;
                }
            }
            createFolderInMinio(fullPath);
            logger.debug("Folder created: {}", fullPath);

        } catch (Exception e) {
            if (strict) {
                throw new RuntimeException("createFolder: " + e.getMessage(), e);
            } else {
                logger.warn("Error creating folder (non-strict mode): {} — {}", fullPath, e.getMessage());
            }
        }
    }

    @Override
    public List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files) {
        List<MinioObject> uploadedObjects = new ArrayList<>();
        try {
            String destination = ensureTrailingSlash(destinationFullPath);

            for (MultipartFile file : files) {
                String objectName = destination + file.getOriginalFilename();
                validateFileCreation(objectName);

                createFileInMinio(objectName, file);

                uploadedObjects.add(MinioObject.builder()
                        .name(file.getOriginalFilename())
                        .path(objectName)
                        .size(file.getSize())
                        .isDirectory(false)
                        .build());
                logger.debug("File uploaded: {}", objectName);
            }

            return uploadedObjects;
        } catch (Exception e) {
            throw new RuntimeException("uploadFiles: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String fullPath) {
        try {
            if (fullPath.endsWith("/") || isDirectory(fullPath)) {
                logger.debug("Deleting folder recursively: {}", fullPath);

                List<String> objectsToDelete = collectAllObjectsRecursive(fullPath);
                objectsToDelete.add(fullPath);

                deleteObjects(objectsToDelete);

                logger.debug("Folder and contents deleted: {}. Objects deleted: {}",
                        fullPath, objectsToDelete.size());
            } else {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(fullPath)
                                .build()
                );
                logger.debug("File deleted: {}", fullPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("deleteObject: " + e.getMessage(), e);
        }
    }

    /**
     * Сбор всех объектов в папке рекурсивно
     */
    private List<String> collectAllObjectsRecursive(String folderPath) {
        List<String> objects = new ArrayList<>();
        try {
            String prefix = ensureTrailingSlash(folderPath);

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                // Skip the folder itself
                if (!objectName.equals(prefix)) {
                    objects.add(objectName);
                }
            }
        } catch (Exception e) {
            logger.error("Error collecting objects in folder: {}", folderPath, e);
            throw new RuntimeException("Error collecting objects in folder: " + folderPath, e);
        }
        return objects;
    }

    @Override
    public void renameObject(String oldFullPath, String newFullPath) {
        try {
            boolean isDirectory = oldFullPath.endsWith("/") || isDirectory(oldFullPath);

            if (isDirectory) {
                renameDirectory(oldFullPath, newFullPath);
            } else {
                renameFile(oldFullPath, newFullPath);
            }

            logger.debug("Object renamed: {} -> {}", oldFullPath, newFullPath);
        } catch (Exception e) {
            logger.error("Error renaming {} -> {}: {}", oldFullPath, newFullPath, e.getMessage(), e);
            throw new RuntimeException("renameObject: " + e.getMessage(), e);
        }
    }

    /**
     * Переименование папки и всего её содержимого
     */
    private void renameDirectory(String oldFolderPath, String newFolderPath) throws Exception {
        String oldPrefix = ensureTrailingSlash(oldFolderPath);
        String newPrefix = ensureTrailingSlash(newFolderPath);

        createFolderInMinio(newPrefix);

        List<String> objectsToRename = collectAllObjectsRecursive(oldPrefix);
        List<String> copiedObjects = new ArrayList<>();

        try {
            for (String oldObjectPath : objectsToRename) {
                String newObjectPath = oldObjectPath.replaceFirst(
                        Pattern.quote(oldPrefix),
                        newPrefix
                );

                // Копируем объект
                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(bucket)
                                .object(newObjectPath)
                                .source(CopySource.builder()
                                        .bucket(bucket)
                                        .object(oldObjectPath)
                                        .build())
                                .build()
                );
                copiedObjects.add(oldObjectPath);
                logger.debug("Copied: {} -> {}", oldObjectPath, newObjectPath);
            }

            deleteObject(oldPrefix);

        } catch (Exception e) {
            logger.error("Error during copy, performing rollback", e);
            rollbackRename(newPrefix, copiedObjects);
            throw e;
        }
    }

    /**
     * Переименование отдельного файла
     */
    private void renameFile(String oldFilePath, String newFilePath) throws Exception {
        // Проверяем существование нового пути
        if (isObjectExists(newFilePath)) {
            throw new RuntimeException("A file with this name already exists: " + newFilePath);
        }

        // Копируем файл
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(bucket)
                        .object(newFilePath)
                        .source(CopySource.builder()
                                .bucket(bucket)
                                .object(oldFilePath)
                                .build())
                        .build()
        );

        // Удаляем старый файл
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(oldFilePath)
                        .build()
        );
    }

    /**
     * Откат операции переименования при ошибке
     */
    private void rollbackRename(String newPrefix, List<String> copiedObjects) {
        logger.warn("Rolling back rename operation for folder: {}", newPrefix);

        for (String oldPath : copiedObjects) {
            try {
                String newPath = oldPath.replaceFirst(
                        Pattern.quote(ensureTrailingSlash(newPrefix)),
                        ""
                );
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(newPath)
                                .build()
                );
            } catch (Exception e) {
                logger.error("Error during rollback for object: {}", oldPath, e);
            }
        }
    }

    @Override
    public List<MinioObject> searchFiles(String userFolder, String query) {
        try {
            String prefix = ensureTrailingSlash(userFolder);
            List<MinioObject> results = new ArrayList<>();
            String queryLower = query.toLowerCase();

            for (Result<Item> result : minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            )) {
                Item item = result.get();
                String fileName = extractName(item.objectName());

                if (fileName.toLowerCase().contains(queryLower)) {
                    results.add(createMinioObject(item));
                }
            }

            return results;
        } catch (Exception e) {
            throw new RuntimeException("searchFiles: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDownloadUrl(String fullPath) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(fullPath)
                            .expiry(60 * 60)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("getDownloadUrl: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isObjectExists(String fullPath) throws Exception {
        try {
            logger.debug("Checking object existence in Minio: {}", fullPath);
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );
            logger.debug("Object exists: {}", fullPath);
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                logger.debug("Object does not exist: {}", fullPath);
                return false;
            }
            logger.error("MinIO error while checking object {}: {}", fullPath, e.getMessage());

            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public MinioObject getObjectInfo(String fullPath) {
        logger.debug("getObjectInfo: fullPath = {}", fullPath);
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );

            return MinioObject.builder()
                    .name(extractName(fullPath))
                    .path(fullPath)
                    .size(stat.size())
                    .isDirectory(fullPath.endsWith("/"))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("getObjectInfo Not Found: " + e.getMessage(), e);
        }
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============
    //   
    private void validateFileCreation(String fullPath) throws Exception {
        logger.debug("Validating file creation for path: {}", fullPath);

        String normalizedPath = fullPath.endsWith("/")
                ? fullPath.substring(0, fullPath.length() - 1)
                : fullPath;

        logger.debug("Normalized path: {}", normalizedPath);

        if (isObjectExists(normalizedPath)) {
            logger.debug("File already exists: {}", normalizedPath);
            throw new RuntimeException("File already exists: " + normalizedPath);
        }

        if (fullPath.contains("/")) {
            int lastSlashIndex = normalizedPath.lastIndexOf('/');
            if (lastSlashIndex > 0) {
                String parentPath = normalizedPath.substring(0, lastSlashIndex);
                if (!parentPath.isEmpty()) {
                    String parentObjectName = ensureTrailingSlash(parentPath);
                    if (!isObjectExists(parentObjectName)) {
                        throw new NoSuchElementException("Parent directory does not exist");
                    }
                }
            }
        }
    }

    private void createFolderInMinio(String fullPath) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(fullPath)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
    }

    private void createFileInMinio(String objectName, MultipartFile file) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
    }

    private MinioObject createMinioObject(Item item) {
        return MinioObject.builder()
                .name(extractName(item.objectName()))
                .path(item.objectName())
                .size(item.size())
                .isDirectory(item.isDir() && item.objectName().endsWith("/"))
                .build();
    }

    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private String extractName(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }

        String path = fullPath.endsWith("/")
                ? fullPath.substring(0, fullPath.length() - 1)
                : fullPath;

        int lastSlash = path.lastIndexOf('/');
        return lastSlash != -1 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Удаление нескольких объектов (используется для рекурсивного удаления
     * папки)
     */
    private void deleteObjects(List<String> objectsToDelete) {
        for (String objectPath : objectsToDelete) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectPath)
                                .build()
                );
            } catch (Exception e) {
                logger.error("Error deleting object: {}", objectPath, e);
                // Продолжаем удаление остальных объектов
            }
        }
    }

    /**
     * Проверка, является ли объект папкой
     */
    private boolean isDirectory(String fullPath) {
        try {
            // Пытаемся получить информацию об объекте
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );

            // Дополнительная проверка через listObjects
            String prefix = ensureTrailingSlash(fullPath);
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .maxKeys(1)
                            .build()
            );

            // Если есть хотя бы один объект с таким префиксом - это папка
            return results.iterator().hasNext();

        } catch (ErrorResponseException e) {
            logger.error("Object not found while checking type: {}", fullPath, e);
            return false;
        } catch (Exception e) {
            logger.error("Error while checking object type: {}", fullPath, e);
            return false;
        }
    }

}
