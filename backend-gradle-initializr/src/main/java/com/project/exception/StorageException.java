package com.project.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class StorageException extends RuntimeException {

    // Базовое исключение для всех ошибок хранилища
    public abstract static class StorageBaseException extends RuntimeException {

        private final Long userId;
        private final String relativePath;
        private final String operation;

        public StorageBaseException(String message, Long userId, String relativePath, String operation) {
            super(message);
            this.userId = userId;
            this.relativePath = relativePath;
            this.operation = operation;
        }

        public StorageBaseException(String message, String operation) {
            super(message);
            this.userId = null;
            this.relativePath = null;
            this.operation = operation;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getOperation() {
            return operation;
        }
    }

    @ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Невалидный путь")
    public static class InvalidPathException extends StorageBaseException {

        public InvalidPathException(String message, Long userId, String path, String operation) {
            super(message, userId, path, operation);
        }

        public InvalidPathException(String message, String path) {
            super(message, path);
        }

        public InvalidPathException(String message) {
            super(message, "path_validation");
        }

    }

    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Ресурс не найден")
    public static class ResourceNotFoundException extends StorageBaseException {

        public ResourceNotFoundException(String message, Long userId, String path, String operation) {
            super(message, userId, path, operation);
        }

        public ResourceNotFoundException(String message, String path) {
            super(message, path);
        }

        public ResourceNotFoundException(String message) {
            super(message, "resource_not_found");
        }
    }

    @ResponseStatus(code = HttpStatus.CONFLICT, reason = "Ресурс уже существует")
    public static class ResourceAlreadyExistsException extends StorageBaseException {

        public ResourceAlreadyExistsException(String message, Long userId, String path, String operation) {
            super(message, userId, path, operation);
        }

        public ResourceAlreadyExistsException(String message, String path) {
            super(message, path);
        }
    }

    @ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Ошибка хранилища")
    public static class StorageOperationException extends StorageBaseException {

        public StorageOperationException(String message, Long userId, String path, String operation) {
            super(message, userId, path, operation);
        }

        public StorageOperationException(String message, String path) {
            super(message, path);
        }
    }
}
