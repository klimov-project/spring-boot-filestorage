package com.project.service;

import com.project.entity.MinioObject;
import io.minio.*;
import io.minio.messages.Item;
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
    public List<MinioObject> listObjects(String userFolder) {
        List<MinioObject> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(userFolder)
                            .recursive(false)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(
                        MinioObject.builder()
                                .name(item.objectName())
                                .path(userFolder)
                                .size(item.size())
                                .isDirectory(item.isDir())
                                .build()
                );
            }
        } catch (Exception e) {
            logger.error("Error listing objects: {}", e.getMessage());
            throw new RuntimeException("Failed to list objects", e);
        }
        return objects;
    }

    @Override
    public void deleteObject(String path) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            logger.error("Error deleting object: {}", e.getMessage());
            throw new RuntimeException("Failed to delete object", e);
        }
    }

    @Override
    public void renameObject(String fromPath, String toPath) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(toPath)
                            .source(CopySource.builder().bucket(bucket).object(fromPath).build())
                            .build()
            );
            deleteObject(fromPath);
        } catch (Exception e) {
            logger.error("Error renaming object: {}", e.getMessage());
            throw new RuntimeException("Failed to rename object", e);
        }
    }

    @Override
    public List<MinioObject> searchFiles(String query) {
        List<MinioObject> results = new ArrayList<>();
        try {
            Iterable<Result<Item>> items = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : items) {
                Item item = result.get();
                if (item.objectName().contains(query)) {
                    results.add(
                            MinioObject.builder()
                                    .name(item.objectName())
                                    .path(item.objectName())
                                    .size(item.size())
                                    .isDirectory(item.isDir())
                                    .build()
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error searching files: {}", e.getMessage());
            throw new RuntimeException("Failed to search files", e);
        }
        return results;
    }

    @Override
    public List<MinioObject> uploadFiles(String destinationPath, MultipartFile[] files) {
        List<MinioObject> uploadedObjects = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String objectName = destinationPath + "/" + file.getOriginalFilename();
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
                                .path(destinationPath)
                                .size(file.getSize())
                                .isDirectory(false)
                                .build()
                );
            }
        } catch (Exception e) {
            logger.error("Error uploading files: {}", e.getMessage());
            throw new RuntimeException("Failed to upload files", e);
        }
        return uploadedObjects;
    }

    @Override
    public void createFolder(String path) {
        try {
            String objectName = path.endsWith("/") ? path : path + "/";
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );
        } catch (Exception e) {
            logger.error("Error creating folder: {}", e.getMessage());
            throw new RuntimeException("Failed to create folder", e);
        }
    }

    @Override
    public String getBucketName() {
        return bucket;
    }
}
