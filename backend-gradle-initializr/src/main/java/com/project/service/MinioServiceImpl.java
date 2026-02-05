package com.project.service;

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
import com.project.storage.util.MinioExceptionHandler;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
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
                objects.add(createMinioObject(item));
            }

            return objects;
        } catch (Exception e) {
            throw MinioExceptionHandler.handleGenericException(e, fullPath, "получении списка");
        }
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
            logger.debug("Объект удалён: {}", fullPath);
        } catch (Exception e) {
            throw MinioExceptionHandler.handleGenericException(e, fullPath, "удалении");
        }
    }

    @Override
    public void renameObject(String oldFullPath, String newFullPath) {
        try {
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
            deleteObject(oldFullPath);
            logger.debug("Объект переименован: {} -> {}", oldFullPath, newFullPath);
        } catch (Exception e) {
            throw MinioExceptionHandler.handleGenericException(e, oldFullPath + " -> " + newFullPath, "переименовании");
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
            throw MinioExceptionHandler.handleGenericException(e, userFolder, "поиске");
        }
    }

    @Override
    public List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files) {
        List<MinioObject> uploadedObjects = new ArrayList<>();

        try {
            String destination = ensureTrailingSlash(destinationFullPath);

            for (MultipartFile file : files) {
                String objectName = destination + file.getOriginalFilename();

                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(file.getInputStream(), file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );

                uploadedObjects.add(MinioObject.builder()
                        .name(file.getOriginalFilename())
                        .path(objectName)
                        .size(file.getSize())
                        .isDirectory(false)
                        .build());

                logger.debug("Файл загружен: {}", objectName);
            }

            return uploadedObjects;
        } catch (Exception e) {
            throw MinioExceptionHandler.handleGenericException(e, destinationFullPath, "загрузке файлов");
        }
    }

    @Override
    public void createFolder(String fullPath) {
        try {
            validateFolderCreation(fullPath);
            createFolderInMinio(fullPath);
            logger.debug("Папка создана: {}", fullPath);
        } catch (Exception e) {
            throw MinioExceptionHandler.handleFolderCreationException(e, fullPath, "создании папки");
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
            throw MinioExceptionHandler.handleGenericException(e, fullPath, "получении ссылки");
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
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw MinioExceptionHandler.handleGenericException(e, fullPath, "проверке существования");
        } catch (Exception e) {
            throw MinioExceptionHandler.handleGenericException(e, fullPath, "проверке существования");
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
            throw MinioExceptionHandler.handleGenericException(e, fullPath, "получении информации");
        }
    }

    @Override
    public String getBucketName() {
        return bucket;
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============
    private void validateFolderCreation(String fullPath) throws Exception {
        // Проверка существования папки
        if (objectExists(fullPath)) {
            throw new IllegalStateException("Папка уже существует");
        }

        // Проверка родительской папки
        if (fullPath.contains("/")) {
            String parentPath = fullPath.substring(0, fullPath.lastIndexOf('/'));
            if (!parentPath.isEmpty()) {
                String parentObjectName = ensureTrailingSlash(parentPath);
                if (!objectExists(parentObjectName)) {
                    throw new NoSuchElementException("Родительская папка не существует");
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

    private MinioObject createMinioObject(Item item) {
        return MinioObject.builder()
                .name(extractName(item.objectName()))
                .path(item.objectName())
                .size(item.size())
                .isDirectory(item.isDir() || item.objectName().endsWith("/"))
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
}
