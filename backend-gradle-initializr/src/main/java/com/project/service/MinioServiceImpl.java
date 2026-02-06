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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.project.service.MinioService;

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

    @Override
    public void createFolder(String fullPath) {
        try {
            validateFolderCreation(fullPath);
            createFolderInMinio(fullPath);
            logger.debug("Папка создана: {}", fullPath);
        } catch (Exception e) {

            // Преобразуем исключения в понятные RuntimeException
            if (e instanceof IllegalStateException) {
                throw new RuntimeException("Папка уже существует: " + extractRelativePath(fullPath));
            } else if (e instanceof NoSuchElementException) {
                throw new RuntimeException("Родительская папка не существует: " + getParentPath(fullPath));
            } else if (e instanceof IllegalArgumentException) {
                throw new RuntimeException("Невалидный путь: " + e.getMessage());
            } else if (e instanceof ErrorResponseException) {
                ErrorResponseException err = (ErrorResponseException) e;
                if ("NoSuchKey".equals(err.errorResponse().code())) {
                    throw new RuntimeException("Ресурс не найден: " + extractRelativePath(fullPath));
                }
            }
            throw new RuntimeException("Ошибка при создании папки: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files) {
        logger.info("=== Initializing uploadFiles IMLEMENT   ===");
        logger.info("=== files: {}   ===", files);
        List<MinioObject> uploadedObjects = new ArrayList<>();

        try {
            logger.info("=== Initializing uploadFiles IMLEMENT 222  ===");
            String destination = ensureTrailingSlash(destinationFullPath);
            logger.info("=== destination: {}   ===", destination);

            for (MultipartFile file : files) {
                logger.info("=== Initializing uploadFiles IMLEMENT 333  ===");
                String objectName = destination + file.getOriginalFilename();
                logger.info("=== objectName: {}   ===", objectName);

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
            throw new RuntimeException(e.getMessage(), e);
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
            if (e instanceof ErrorResponseException err) {
                if ("NoSuchKey".equals(err.errorResponse().code())) {
                    throw new RuntimeException("Ресурс не найден: " + extractRelativePath(fullPath));
                }
            }
            throw new RuntimeException("Ошибка при удалении: " + e.getMessage(), e);
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
            throw new RuntimeException(e.getMessage(), e);
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
            throw new RuntimeException(e.getMessage(), e);
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
            throw new RuntimeException(e.getMessage(), e);
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
            throw new RuntimeException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
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
            if (e instanceof ErrorResponseException) {
                ErrorResponseException err = (ErrorResponseException) e;
                if ("NoSuchKey".equals(err.errorResponse().code())) {
                    throw new RuntimeException("Ресурс не найден: " + extractRelativePath(fullPath));
                }
            }
            throw new RuntimeException("Ошибка при получении информации: " + e.getMessage(), e);
        }
    }

    @Override
    public String getBucketName() {
        return bucket;
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============
    private void validateFolderCreation(String fullPath) throws Exception {
        // Удаляем завершающий слеш если есть
        String normalizedPath = fullPath.endsWith("/")
                ? fullPath.substring(0, fullPath.length() - 1)
                : fullPath;

        // Проверка существования папки
        if (objectExists(normalizedPath + "/")) {
            throw new IllegalStateException("Папка уже существует");
        }

        // Проверка родительской папки
        if (fullPath.contains("/")) {
            // Находим последний слеш
            int lastSlashIndex = normalizedPath.lastIndexOf('/');
            if (lastSlashIndex > 0) {
                // Берем путь до последнего слеша 
                String parentPath = normalizedPath.substring(0, lastSlashIndex);

                if (!parentPath.isEmpty()) {
                    String parentObjectName = ensureTrailingSlash(parentPath);
                    if (!objectExists(parentObjectName)) {
                        throw new NoSuchElementException("Родительская папка не существует");
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

    private String extractRelativePath(String fullPath) {
        // Извлекаем часть пути после user-{id}-files/
        String[] parts = fullPath.split("/");
        if (parts.length > 1 && parts[0].startsWith("user-") && parts[0].endsWith("-files")) {
            if (parts.length == 1) {
                return "/";
            }
            StringBuilder relative = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                relative.append(parts[i]);
                if (i < parts.length - 1 || fullPath.endsWith("/")) {
                    relative.append("/");
                }
            }
            return relative.toString();
        }
        return fullPath;
    }

    private String getParentPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }

        if (fullPath.endsWith("/")) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }

        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash == -1) {
            return "";
        }

        return fullPath.substring(0, lastSlash + 1);
    }
}
