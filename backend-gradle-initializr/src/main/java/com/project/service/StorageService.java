package com.project.service;

import com.project.storage.dto.ResourceInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StorageService {

    public void createUserDirectory(Long id);

    /**
     * Создание пустой папки
     */
    ResourceInfo createDirectory(Long userId, String path)
            throws InvalidPathException, ResourceNotFoundException, ResourceAlreadyExistsException;

    /**
     * Получение информации о ресурсе
     */
    ResourceInfo getResourceInfo(Long userId, String path)
            throws ResourceNotFoundException, InvalidPathException;

    /**
     * Удаление ресурса
     */
    void deleteResource(Long userId, String path)
            throws ResourceNotFoundException, InvalidPathException;

    /**
     * Переименование/перемещение ресурса
     */
    ResourceInfo moveResource(Long userId, String fromPath, String toPath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException, InvalidPathException;

    /**
     * Поиск ресурсов по имени
     */
    List<ResourceInfo> searchResources(Long userId, String query)
            throws InvalidPathException;

    /**
     * Загрузка файлов
     */
    List<ResourceInfo> uploadFiles(Long userId, String destinationPath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException;

    /**
     * Получение содержимого папки
     */
    List<ResourceInfo> getDirectoryContents(Long userId, String path)
            throws ResourceNotFoundException, InvalidPathException;

    // Оставляем существующие классы исключений для обратной совместимости
    @ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Невалидный путь")
    class InvalidPathException extends RuntimeException {

        public InvalidPathException(String message) {
            super(message);
        }
    }

    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Ресурс не найден")
    class ResourceNotFoundException extends RuntimeException {

        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    @ResponseStatus(code = HttpStatus.CONFLICT, reason = "Ресурс уже существует")
    class ResourceAlreadyExistsException extends RuntimeException {

        public ResourceAlreadyExistsException(String message) {
            super(message);
        }
    }
}
