package com.project.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MinioService {

    List<MinioObject> listObjects(String fullPath);

    void deleteObject(String fullPath);

    void renameObject(String oldFullPath, String newFullPath);

    List<MinioObject> searchFiles(String userFolder, String query);

    List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files);

    void createFolder(String fullPath);

    // Дополнительные методы
    /**
     * Получение URL для скачивания файла
     */
    String getDownloadUrl(String fullPath);

    /**
     * Проверка существования объекта
     */
    boolean objectExists(String fullPath);

    /**
     * Получение информации об объекте
     */
    MinioObject getObjectInfo(String fullPath);
}
