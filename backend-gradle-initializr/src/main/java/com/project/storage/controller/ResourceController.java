package com.project.storage.controller;

import com.project.entity.MinioObject;
import com.project.service.MinioService;

import com.project.storage.dto.ResourceInfo;
import com.project.storage.service.StorageService;
import com.project.storage.service.DownloadService;
import com.project.storage.util.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private static final Logger logger = LoggerFactory.getLogger(ResourceController.class);

    private final StorageService storageService;
    private final MinioService minioService;
    private final PathValidator pathValidator;
    private final DownloadService downloadService;

    public ResourceController(
            MinioService minioService,
            StorageService storageService,
            PathValidator pathValidator,
            DownloadService downloadService) {
        this.minioService = minioService;
        this.storageService = storageService;
        this.pathValidator = pathValidator;
        this.downloadService = downloadService;
    }

    /**
     * GET /resource - Получение информации о ресурсе
     */
    @GetMapping("/resource")
    public ResponseEntity<?> getResourceInfo(@RequestParam String path) {
        logger.info("GET /resource called with path: {}", path);
        try {
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);
            ResourceInfo info = storageService.getResourceInfo(path);
            logger.info("Resource info retrieved successfully for: {}", path);
            return ResponseEntity.ok(info);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("Invalid path: {} - {}", path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("Resource not found: {} - {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error getting resource info for path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error getting resource info: " + e.getMessage());
        }
    }

    /**
     * DELETE /resource - Удаление ресурса
     */
    @DeleteMapping("/resource")
    public ResponseEntity<?> deleteResource(@RequestParam String path) {
        logger.info("DELETE /resource called with path: {}", path);
        try {
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);
            storageService.deleteResource(path);
            return ResponseEntity.noContent().build();
        } catch (StorageService.InvalidPathException e) {
            logger.warn("Invalid path: {} - {}", path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("Resource not found: {} - {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting resource for path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error deleting resource: " + e.getMessage());
        }
    }

    /**
     * GET /resource/move - Переименование/перемещение ресурса
     */
    @GetMapping("/resource/move")
    public ResponseEntity<?> moveResource(
            @RequestParam String from,
            @RequestParam String to) {
        logger.info("GET /resource/move called: from={}, to={}", from, to);
        try {
            validatePath(from);
            validatePath(to);
            logger.debug("Path validation passed: from={}, to={}", from, to);
            ResourceInfo info = storageService.moveResource(from, to);
            logger.info("Resource moved successfully: {} -> {}", from, to);
            return ResponseEntity.ok(info);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("Invalid path: from={}, to={} - {}", from, to, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("Resource not found: from={}, to={} - {}", from, to, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            logger.warn("Resource already exists: from={}, to={} - {}", from, to, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error moving resource: {} -> {}", from, to, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error moving resource: " + e.getMessage());
        }
    }

    /**
     * GET /resource/search - Поиск ресурсов
     */
    @GetMapping("/resource/search")
    public ResponseEntity<?> searchResources(@RequestParam String query) {
        logger.info("GET /resource/search called with query: {}", query);
        try {
            if (query == null || query.trim().isEmpty()) {
                logger.warn("Empty search query received");
                return ResponseEntity.badRequest().body("Search query is required");
            }
            List<ResourceInfo> results = storageService.searchResources(query);
            logger.info("Search completed, found {} results for query: {}", results.size(), query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching resources with query: {}", query, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error searching resources: " + e.getMessage());
        }
    }

    /**
     * POST /resource - Загрузка файлов
     */
    @PostMapping("/resource")
    public ResponseEntity<?> uploadResource(
            @RequestParam String path,
            @RequestParam("files") MultipartFile[] files) {
        logger.info("POST /resource called with path: {}, files count: {}", path, files != null ? files.length : 0);
        try {
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);
            if (files == null || files.length == 0) {
                logger.warn("No files provided for upload");
                return ResponseEntity.badRequest().body("No files provided");
            }
            List<ResourceInfo> uploaded = storageService.uploadFiles(path, files);
            logger.info("Successfully uploaded {} files to {}", uploaded.size(), path);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("Invalid path: {} - {}", path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            logger.warn("Resource already exists: {} - {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error uploading files to path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error uploading files: " + e.getMessage());
        }
    }

    /**
     * GET /directory - Получение содержимого папки
     */
    @GetMapping("/directory")
    public ResponseEntity<?> getDirectoryContents(@RequestParam String path) {
        logger.info("GET /directory called with path: {}", path);
        try {
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);

            // Получаем список объектов из MinIO
            List<MinioObject> contents = minioService.listObjects(path);
            logger.info("Retrieved {} items from MinIO directory: {}", contents.size(), path);

            return ResponseEntity.ok(contents);
        } catch (Exception e) {
            logger.error("Error getting directory contents from MinIO for path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error getting directory contents: " + e.getMessage());
        }
    }

    /**
     * POST /directory - Создание пустой папки
     */
    @PostMapping("/directory")
    public ResponseEntity<?> createDirectory(@RequestParam String path) {
        logger.info("POST /directory called with path: {}", path);
        try {
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);
            ResourceInfo created = storageService.createDirectory(path);
            logger.info("Directory created successfully: {}", path);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (StorageService.InvalidPathException e) {
            logger.warn("Invalid path: {} - {}", path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("Parent directory not found: {} - {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            logger.warn("Directory already exists: {} - {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating directory with path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error creating directory: " + e.getMessage());
        }
    }

    private void validatePath(String path) throws StorageService.InvalidPathException {
        if (path == null || path.trim().isEmpty()) {
            throw new StorageService.InvalidPathException("Path is null or empty");
        }
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageService.InvalidPathException("Invalid path format. Note: folders must end with '/'");
        }
    }

    /**
     * GET /resource/download - Скачивание ресурса
     */
    @GetMapping("/resource/download")
    public ResponseEntity<?> downloadResource(
            @RequestParam String path,
            HttpServletResponse response) {

        logger.info("GET /resource/download called with path: {}", path);

        try {
            validatePath(path);
            logger.debug("Path validation passed for download: {}", path);

            DownloadService.DownloadResult downloadResult = downloadService.getDownloadResource(path);
            Resource resource = downloadResult.getResource();

            if (!resource.exists()) {
                logger.warn("Download resource not found: {}", path);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Resource not found");
            }

            // Настройка заголовков ответа
            String contentType = downloadResult.isZip()
                    ? "application/zip"
                    : "application/octet-stream";

            String contentDisposition = "attachment; filename=\""
                    + downloadResult.getFilename() + "\"";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);

        } catch (StorageService.InvalidPathException e) {
            logger.warn("Invalid path for download: {} - {}", path, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            logger.warn("Resource not found for download: {} - {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            logger.error("IO error during download for path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Error reading resource: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unknown error during download for path: {}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Unknown error downloading resource");
        }
    }
}
