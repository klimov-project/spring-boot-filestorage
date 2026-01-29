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
    private final PathValidator pathValidator;

    public ResourceController(StorageService storageService, PathValidator pathValidator) {
        this.storageService = storageService;
        this.pathValidator = pathValidator;
    }

    @GetMapping("/resource")
    public ResponseEntity<?> getResourceInfo(@RequestParam String path) {
        logger.info("GET /resource called with path: {}", path);
        try {
            validatePath(path);
            logger.debug("Path validation passed for: {}", path);

            ResourceInfo info = storageService.getResourceInfo(path);

            if ("/".equals(path)) {
                return ResponseEntity.ok("you are in the root Minio Bucket");
            }

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

    @DeleteMapping("/resource")
    public ResponseEntity<?> deleteResource(@RequestParam String path) {
        logger.info("DELETE /resource called with path: {}", path);
        try {
            validatePath(path);
            storageService.deleteResource(path);
            return ResponseEntity.noContent().build();
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/resource/move")
    public ResponseEntity<?> moveResource(@RequestParam String from, @RequestParam String to) {
        logger.info("GET /resource/move called: from={}, to={}", from, to);
        try {
            validatePath(from);
            validatePath(to);
            ResourceInfo info = storageService.moveResource(from, to);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/resource/search")
    public ResponseEntity<?> searchResources(@RequestParam String query) {
        logger.info("GET /resource/search called with query: {}", query);
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Search query is required");
            }
            List<ResourceInfo> results = storageService.searchResources(query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/resource")
    public ResponseEntity<?> uploadResource(@RequestParam String path, @RequestParam("files") MultipartFile[] files) {
        logger.info("POST /resource called with path: {}, files count: {}", path, files != null ? files.length : 0);
        try {
            validatePath(path);
            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().body("No files provided");
            }
            List<ResourceInfo> uploaded = storageService.uploadFiles(path, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/directory")
    public ResponseEntity<?> getDirectoryContents(@RequestParam String path) {
        logger.info("GET /directory called with path: {}", path);
        try {
            validatePath(path);
            List<ResourceInfo> contents = storageService.getDirectoryContents(path);
            return ResponseEntity.ok(contents);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/directory")
    public ResponseEntity<?> createDirectory(@RequestParam String path) {
        logger.info("POST /directory called with path: {}", path);
        try {
            validatePath(path);
            ResourceInfo created = storageService.createDirectory(path);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    private void validatePath(String path) throws StorageService.InvalidPathException {
        if (path == null || path.trim().isEmpty()) {
            throw new StorageService.InvalidPathException("Path is null or empty");
        }
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageService.InvalidPathException("Invalid path format");
        }
    }
}
