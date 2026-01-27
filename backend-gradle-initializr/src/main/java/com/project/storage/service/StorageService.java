package com.project.storage.service;

import com.project.storage.dto.ResourceInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StorageService {

    /**
     * Получение информации о ресурсе
     */
    ResourceInfo getResourceInfo(String path) throws ResourceNotFoundException;

    /**
     * Удаление ресурса
     */
    void deleteResource(String path) throws ResourceNotFoundException;

    /**
     * Переименование/перемещение ресурса
     */
    ResourceInfo moveResource(String fromPath, String toPath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException, InvalidPathException;

    /**
     * Поиск ресурсов по имени
     */
    List<ResourceInfo> searchResources(String query);

    /**
     * Загрузка файлов
     */
    List<ResourceInfo> uploadFiles(String destinationPath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException;

    /**
     * Получение содержимого папки
     */
    List<ResourceInfo> getDirectoryContents(String path) throws ResourceNotFoundException;

    /**
     * Создание пустой папки
     */
    ResourceInfo createDirectory(String path)
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
