package com.project.storage.service;

import com.project.storage.dto.ResourceInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StorageService {

    public void createUserDirectory(Long id);
    /**
     * Получение информации о ресурсе
     */
    ResourceInfo getResourceInfo(Long userId, String path)
            throws ResourceNotFoundException;

    /**
     * Удаление ресурса
     */
    void deleteResource(Long userId, String path)
            throws ResourceNotFoundException;

    /**
     * Переименование/перемещение ресурса
     */
    ResourceInfo moveResource(Long userId, String fromPath, String toPath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException, InvalidPathException;

    /**
     * Поиск ресурсов по имени
     */
    List<ResourceInfo> searchResources(Long userId, String query);

    /**
     * Загрузка файлов
     */
    List<ResourceInfo> uploadFiles(Long userId, String destinationPath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException;

    /**
     * Получение содержимого папки
     */
    List<ResourceInfo> getDirectoryContents(Long userId, String path)
            throws ResourceNotFoundException;

    /**
     * Создание пустой папки
     */
    ResourceInfo createDirectory(Long userId, String path)
            throws InvalidPathException, ResourceNotFoundException, ResourceAlreadyExistsException;


    // Исключения для обработки ошибок
    class ResourceNotFoundException extends Exception {

        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    class ResourceAlreadyExistsException extends Exception {

        public ResourceAlreadyExistsException(String message) {
            super(message);
        }
    }

    class InvalidPathException extends Exception {

        public InvalidPathException(String message) {
            super(message);
        }
    }
}
