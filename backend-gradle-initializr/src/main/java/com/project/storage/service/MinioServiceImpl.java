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
            // TODO: Валидация пути (например, запрещённые символы и т.д.)
            // pathValidator.validateFolderPath(fullPath);

            // Проверяем, существует ли уже объект с таким именем 
            boolean exists = isObjectExists(fullPath);

            if (exists) {
                if (strict) {
                    throw new RuntimeException("Folder already exists error on 'createFolder' method. ");
                } else {
                    // В нестрогом режиме просто выходим, не создаём и не кидаем ошибку
                    logger.debug("Папка уже существует (нестрогий режим): {}", fullPath);
                    return;
                }
            }
            createFolderInMinio(fullPath);
            logger.debug("Папка создана: {}", fullPath);

        } catch (Exception e) {
            if (strict) {
                throw new RuntimeException("createFolder: " + e.getMessage(), e);
            } else {
                // В нестрогом режиме просто логируем и продолжаем
                logger.warn("Ошибка при создании папки (нестрогий режим): {} — {}", fullPath, e.getMessage());
            }
        }
    }

    @Override
    public List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files) {
        logger.info("=== Initializing uploadFiles IMLEMENT   ===");
        List<MinioObject> uploadedObjects = new ArrayList<>();

        try {
            logger.info("=== Initializing uploadFiles IMLEMENT 222  ===");
            String destination = ensureTrailingSlash(destinationFullPath);
            logger.info("=== destination: {}   ===", destination);

            for (MultipartFile file : files) {
                logger.info("=== Initializing uploadFiles IMLEMENT 333  ===");
                String objectName = destination + file.getOriginalFilename();
                validateFileCreation(objectName);
                logger.info("=== objectName: {}   ===", objectName);

                createFileInMinio(objectName, file);

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
            throw new RuntimeException("uploadFiles: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String fullPath) {
        try {
            // Проверяем, является ли объект папкой
            if (fullPath.endsWith("/") || isDirectory(fullPath)) {
                logger.debug("Удаление папки (рекурсивно): {}", fullPath);

                // Получаем все объекты в папке рекурсивно (одним запросом)
                List<String> objectsToDelete = collectAllObjectsRecursive(fullPath);

                // Добавляем саму папку
                objectsToDelete.add(fullPath);

                // Удаляем все объекты
                deleteObjects(objectsToDelete);

                logger.debug("Папка и содержимое удалены: {}. Объектов: {}",
                        fullPath, objectsToDelete.size());
            } else {
                // Просто удаляем файл
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(fullPath)
                                .build()
                );
                logger.debug("Файл удалён: {}", fullPath);
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

                // Пропускаем саму папку
                if (!objectName.equals(prefix)) {
                    objects.add(objectName);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при сборе объектов в папке: {}", folderPath, e);
            throw new RuntimeException("Ошибка при сборе объектов в папке: " + folderPath, e);
        }

        return objects;
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
            throw new RuntimeException("renameObject: " + e.getMessage(), e);
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
    public boolean isObjectExists(String fullPath) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(fullPath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public MinioObject getObjectInfo(String fullPath) {
        logger.debug("getObjectInfo:  fullPath = {}", fullPath);
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
            throw new RuntimeException("getObjectInfo " + e.getMessage(), e);
        }
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============
    //  
    private void validateFileCreation(String fullPath) throws Exception {
        logger.info("=== Initializing validateFileCreation ===");
        logger.info("  fullPath: {}  ", fullPath);
        // Удаляем завершающий слеш если есть
        String normalizedPath = fullPath.endsWith("/")
                ? fullPath.substring(0, fullPath.length() - 1)
                : fullPath;

        logger.info("  normalizedPath: {}  ", normalizedPath);
        // Проверка существования файла
        if (isObjectExists(normalizedPath)) {
            logger.info(" ==  File already exists 77777 {}", normalizedPath);
            throw new RuntimeException("File already exists 77777 : " + normalizedPath);
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
                    if (!isObjectExists(parentObjectName)) {
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
                logger.error("Ошибка при удалении объекта: {}", objectPath, e);
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
            logger.error("Объект не найден при проверке типа: {}", fullPath, e);
            return false;
        } catch (Exception e) {
            logger.error("Ошибка при проверке типа объекта: {}", fullPath, e);
            return false;
        }
    }

}
