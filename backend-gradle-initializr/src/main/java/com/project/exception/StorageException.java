package com.project.storage.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class StorageException {

    @ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Невалидный путь")
    public static class InvalidPathException extends com.project.service.StorageService.InvalidPathException {

        private final Long userId;
        private final String relativePath;

        public InvalidPathException(String message, Long userId, String relativePath) {
            super(message);
            this.userId = userId;
            this.relativePath = relativePath;
        }

        public InvalidPathException(String message) {
            super(message);
            this.userId = null;
            this.relativePath = null;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Ресурс не найден")
    public static class ResourceNotFoundException extends com.project.service.StorageService.ResourceNotFoundException {

        private final Long userId;
        private final String relativePath;

        public ResourceNotFoundException(String message, Long userId, String relativePath) {
            super(message);
            this.userId = userId;
            this.relativePath = relativePath;
        }

        public ResourceNotFoundException(String message) {
            super(message);
            this.userId = null;
            this.relativePath = null;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    @ResponseStatus(code = HttpStatus.CONFLICT, reason = "Ресурс уже существует")
    public static class ResourceAlreadyExistsException extends com.project.service.StorageService.ResourceAlreadyExistsException {

        private final Long userId;
        private final String relativePath;

        public ResourceAlreadyExistsException(String message, Long userId, String relativePath) {
            super(message);
            this.userId = userId;
            this.relativePath = relativePath;
        }

        public ResourceAlreadyExistsException(String message) {
            super(message);
            this.userId = null;
            this.relativePath = null;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }
}
