package com.project.storage.service;

import com.project.storage.dto.ResourceInfo;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface StorageService {

    public void createUserDirectory(Long id);

    /**
     * Создание пустой папки
     */
    ResourceInfo createDirectory(Long userId, String path);

    /**
     * Получение информации о ресурсе
     */
    ResourceInfo getResourceInfo(Long userId, String path);

    /**
     * Удаление ресурса
     */
    void deleteResource(Long userId, String path);

    /**
     * Переименование/перемещение ресурса
     */
    ResourceInfo moveResource(Long userId, String fromPath, String toPath);

    /**
     * Поиск ресурсов по имени
     */
    List<ResourceInfo> searchResources(Long userId, String query);

    /**
     * Загрузка файлов
     */
    List<ResourceInfo> uploadFiles(Long userId, String destinationPath, MultipartFile[] files);

    /**
     * Получение содержимого папки
     */
    List<ResourceInfo> getDirectoryContents(Long userId, String path);
}
