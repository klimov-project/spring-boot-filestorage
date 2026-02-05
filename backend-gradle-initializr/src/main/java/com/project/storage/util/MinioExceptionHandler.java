package com.project.storage.util;

import io.minio.errors.ErrorResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MinioExceptionHandler {

    public static ResponseStatusException handleFolderCreationException(
            Exception e, String fullPath, String operation) {

        if (e instanceof IllegalStateException) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Папка уже существует: " + fullPath
            );
        } else if (e instanceof IllegalArgumentException) {
            return new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Невалидный путь: " + e.getMessage()
            );
        } else if (e instanceof ErrorResponseException) {
            ErrorResponseException ere = (ErrorResponseException) e;
            if ("NoSuchKey".equals(ere.errorResponse().code())) {
                return new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ресурс не найден: " + fullPath
                );
            }
            return new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ошибка MinIO: " + ere.getMessage()
            );
        } else {
            return new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Неизвестная ошибка при " + operation + ": " + e.getMessage()
            );
        }
    }

    public static RuntimeException handleGenericException(
            Exception e, String context, String operation) {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка при " + operation + " " + context + ": " + e.getMessage()
        );
    }
}
