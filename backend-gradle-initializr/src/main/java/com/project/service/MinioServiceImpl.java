package com.project.service;

import com.project.entity.MinioObject;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.http.Method; 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class MinioServiceImpl implements MinioService {

    private static final Logger logger = LoggerFactory.getLogger(MinioServiceImpl.class);

    @Value("${spring.minio.bucket}")
    private String bucket;

    private final MinioClient minioClient;

    public MinioServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
        logger.info("MinioServiceImpl initialized with bucket: {}", bucket);
    }

    @Override
    public List<MinioObject> listObjects(String fullPath) {
        List<MinioObject> objects = new ArrayList<>();
        try {
            // Убедимся, что path заканчивается слэшом для папок
            String prefix = fullPath.endsWith("/") ? fullPath : fullPath + "/";

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(false)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(
                        MinioObject.builder()
                                .name(extractName(item.objectName()))
                                .path(item.objectName())
                                .size(item.size())
                                .isDirectory(item.isDir() || item.objectName().endsWith("/"))
                                .build()
                );
            }
        } catch (Exception e) {
            logger.error("Error listing objects for path {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Failed to list objects: " + e.getMessage(), e);
        }
        return objects;
    }

    @Override
    public void deleteObject(String fullPath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );
            logger.debug("Object deleted successfully: {}", fullPath);
        } catch (Exception e) {
            logger.error("Error deleting object {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Failed to delete object: " + e.getMessage(), e);
        }
    }

    @Override
    public void renameObject(String oldFullPath, String newFullPath) {
        try {
            // Копируем объект
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(newFullPath)
                            .source(CopySource.builder()
                                    .bucket(bucket)
                                    .object(oldFullPath)
                                    .build())
                            .build()
            );

            // Удаляем старый объект
            deleteObject(oldFullPath);

            logger.debug("Object renamed from {} to {}", oldFullPath, newFullPath);
        } catch (Exception e) {
            logger.error("Error renaming object from {} to {}: {}",
                    oldFullPath, newFullPath, e.getMessage());
            throw new RuntimeException("Failed to rename object: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MinioObject> searchFiles(String userFolder, String query) {
        List<MinioObject> results = new ArrayList<>();
        try {
            // Ищем только в папке пользователя
            String prefix = userFolder.endsWith("/") ? userFolder : userFolder + "/";

            Iterable<Result<Item>> items = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : items) {
                Item item = result.get();
                String objectName = item.objectName();
                String fileName = extractName(objectName);

                // Ищем в имени файла (регистронезависимо)
                if (fileName.toLowerCase().contains(query.toLowerCase())) {
                    results.add(
                            MinioObject.builder()
                                    .name(fileName)
                                    .path(objectName)
                                    .size(item.size())
                                    .isDirectory(item.isDir() || objectName.endsWith("/"))
                                    .build()
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error searching files with query {} in {}: {}",
                    query, userFolder, e.getMessage());
            throw new RuntimeException("Failed to search files: " + e.getMessage(), e);
        }
        return results;
    }

    @Override
    public List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files) {
        List<MinioObject> uploadedObjects = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                // Убедимся, что destinationPath заканчивается слэшом
                String destination = destinationFullPath.endsWith("/")
                        ? destinationFullPath
                        : destinationFullPath + "/";

                String objectName = destination + file.getOriginalFilename();

                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(file.getInputStream(), file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );

                uploadedObjects.add(
                        MinioObject.builder()
                                .name(file.getOriginalFilename())
                                .path(objectName)
                                .size(file.getSize())
                                .isDirectory(false)
                                .build()
                );

                logger.debug("File uploaded successfully: {}", objectName);
            }
        } catch (Exception e) {
            logger.error("Error uploading files to {}: {}", destinationFullPath, e.getMessage());
            throw new RuntimeException("Failed to upload files: " + e.getMessage(), e);
        }

        return uploadedObjects;
    }

    @Override
    public void createFolder(String fullPath) {
        try {
            // Папки в MinIO создаются как объекты с "/" в конце
            String objectName = fullPath.endsWith("/") ? fullPath : fullPath + "/";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );

            logger.debug("Folder created successfully: {}", objectName);
        } catch (Exception e) {
            logger.error("Error creating folder {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Failed to create folder: " + e.getMessage(), e);
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
                            .expiry(60 * 60) // 1 час
                            .build()
            );
        } catch (Exception e) {
            logger.error("Error getting download URL for {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Failed to get download URL: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean objectExists(String fullPath) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            // Объект не существует
            return false;
        } catch (Exception e) {
            logger.error("Error checking if object exists {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Failed to check object existence: " + e.getMessage(), e);
        }
    }

    @Override
    public MinioObject getObjectInfo(String fullPath) {
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
            logger.error("Error getting object info for {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Failed to get object info: " + e.getMessage(), e);
        }
    }

    /**
     * Вспомогательный метод для извлечения имени файла из полного пути
     */
    private String extractName(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }

        // Убираем слэш в конце для папок
        String path = fullPath.endsWith("/")
                ? fullPath.substring(0, fullPath.length() - 1)
                : fullPath;

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            return path.substring(lastSlash + 1);
        }

        return path;
    }
}
