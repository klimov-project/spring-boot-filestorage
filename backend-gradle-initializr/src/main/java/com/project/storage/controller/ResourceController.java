package com.project.storage.controller;

import com.project.entity.User;
import com.project.storage.dto.ResourceInfo;
import com.project.service.StorageService;
import com.project.storage.util.PathValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class ResourceController {

    private static final Logger logger = LoggerFactory.getLogger(ResourceController.class);

    private final StorageService StorageService;
    private final PathValidator pathValidator;

    public ResourceController(StorageService StorageService, PathValidator pathValidator) {
        this.StorageService = StorageService;
        this.pathValidator = pathValidator;
    }

    /**
     * GET /api/resource - Получение информации о файле/папке
     */
    @GetMapping("/resource")
    public ResponseEntity<?> getResourceInfo(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested /resource with path: {}", user.getId(), path);

        try {
            ResponseEntity<?> authCheck = checkAuthResponce(user);
            if (authCheck != null) {
                return authCheck;
            }
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);

            ResourceInfo info = StorageService.getResourceInfo(user.getId(), path);

            logger.info("Resource info retrieved successfully for user {}: {}",
                    user.getId(), path);
            return ResponseEntity.ok(info);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("User {}: Invalid path: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("User {}: Resource not found: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error getting resource info for user {} path {}: {}",
                    user.getId(), path, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error getting resource info: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/resource - Удаление ресурса
     */
    @DeleteMapping("/resource")
    public ResponseEntity<?> deleteResource(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested DELETE /resource with path: {}", user.getId(), path);

        try {
            ResponseEntity<?> authCheck = checkAuthResponce(user);
            if (authCheck != null) {
                return authCheck;
            }
            validatePath(path);
            StorageService.deleteResource(user.getId(), path);

            logger.info("User {} successfully deleted resource: {}",
                    user.getId(), path);
            return ResponseEntity.noContent().build();
        } catch (StorageService.InvalidPathException e) {
            logger.warn("User {}: Invalid path: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("User {}: Resource not found: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting resource for user {} path {}: {}",
                    user.getId(), path, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error deleting resource: " + e.getMessage());
        }
    }

    /**
     * PUT /api/resource/move - Переименование/перемещение ресурса
     */
    @PutMapping("/resource/move")
    public ResponseEntity<?> moveResource(
            @AuthenticationPrincipal User user,
            @RequestParam String from,
            @RequestParam String to) {

        logger.info("User {} requested PUT /resource/move from: {} to: {}",
                user.getId(), from, to);

        try {
            ResponseEntity<?> authCheck = checkAuthResponce(user);
            if (authCheck != null) {
                return authCheck;
            }
            validatePath(from);
            validatePath(to);

            ResourceInfo movedResource = StorageService.moveResource(user.getId(), from, to);

            logger.info("User {} successfully moved resource from {} to {}",
                    user.getId(), from, to);
            return ResponseEntity.ok(movedResource);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("User {}: Invalid path: from={}, to={} - {}",
                    user.getId(), from, to, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("User {}: Resource not found: {} - {}",
                    user.getId(), from, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            logger.warn("User {}: Resource already exists: {} - {}",
                    user.getId(), to, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error moving resource for user {} from {} to {}: {}",
                    user.getId(), from, to, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error moving resource: " + e.getMessage());
        }
    }

    /**
     * GET /api/resource/search - Поиск ресурсов по имени
     */
    @GetMapping("/resource/search")
    public ResponseEntity<?> searchResources(
            @AuthenticationPrincipal User user,
            @RequestParam String query) {

        logger.info("User {} requested GET /resource/search with query: {}",
                user.getId(), query);

        try {
            if (query == null || query.trim().isEmpty()) {
                logger.warn("User {}: Empty search query", user.getId());
                return ResponseEntity.badRequest().body("Search query is required");
            }

            List<ResourceInfo> results = StorageService.searchResources(user.getId(), query);

            logger.info("User {} found {} results for query: {}",
                    user.getId(), results.size(), query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching resources for user {} query {}: {}",
                    user.getId(), query, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error searching resources: " + e.getMessage());
        }
    }

    /**
     * POST /api/resource - Загрузка файлов
     */
    @PostMapping("/resource")
    public ResponseEntity<?> uploadResource(
            @AuthenticationPrincipal User user,
            @RequestParam String path,
            @RequestParam("files") MultipartFile[] files) {

        logger.info("User {} requested POST /resource to path: {} with {} files",
                user.getId(), path, files != null ? files.length : 0);

        try {
            validatePath(path);

            if (files == null || files.length == 0) {
                logger.warn("User {}: No files provided for upload", user.getId());
                return ResponseEntity.badRequest().body("No files provided");
            }

            List<ResourceInfo> uploaded = StorageService.uploadFiles(user.getId(), path, files);

            logger.info("User {} successfully uploaded {} files to path: {}",
                    user.getId(), uploaded.size(), path);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("User {}: Invalid path: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            logger.warn("User {}: Resource already exists in path: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error uploading files for user {} to path {}: {}",
                    user.getId(), path, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error uploading files: " + e.getMessage());
        }
    }

    /**
     * GET /api/directory - Получение содержимого папки
     */
    @GetMapping("/directory")
    public ResponseEntity<?> getDirectoryContents(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested GET /directory with path: {}", user.getId(), path);

        try {
            validatePath(path);
            List<ResourceInfo> contents = StorageService.getDirectoryContents(user.getId(), path);

            logger.info("User {} retrieved {} items from directory: {}",
                    user.getId(), contents.size(), path);
            return ResponseEntity.ok(contents);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("User {}: Invalid path: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("User {}: Directory not found: {} - {}",
                    user.getId(), path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error getting directory contents for user {} path {}: {}",
                    user.getId(), path, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error getting directory contents: " + e.getMessage());
        }
    }

    /**
     * POST /api/directory - Создание пустой папки
     */
    @PostMapping("/directory")
    public ResponseEntity<ResourceInfo> createDirectory(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested POST /directory with path: {}", user.getId(), path);

        ResourceInfo created = StorageService.createDirectory(user.getId(), path);

        logger.info("User {} successfully created directory: {}", user.getId(), path);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Валидация пути
     */
    private void validatePath(String path) throws StorageService.InvalidPathException {
        if (path == null || path.trim().isEmpty()) {
            throw new StorageService.InvalidPathException("Path is null or empty");
        }
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageService.InvalidPathException("Invalid path format: " + path);
        }
    }

    /**
     * Кастомный метод для проверки аутентификации пользователя
     */
    private ResponseEntity<?> checkAuthResponce(User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"message\": \"Пользователь не авторизован\"}");
        }
        return null;
    }
}
