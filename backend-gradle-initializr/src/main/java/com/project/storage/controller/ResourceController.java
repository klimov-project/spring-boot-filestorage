package com.project.storage.controller;

import com.project.storage.dto.ResourceInfo;
import com.project.storage.service.StorageService;
import com.project.storage.util.PathValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private final StorageService storageService;
    private final PathValidator pathValidator;

    public ResourceController(StorageService storageService, PathValidator pathValidator) {
        this.storageService = storageService;
        this.pathValidator = pathValidator;
    }

    /**
     * GET /resource - Получение информации о ресурсе
     */
    @GetMapping("/resource")
    public ResponseEntity<?> getResourceInfo(@RequestParam String path) {
        try {
            validatePath(path);
            ResourceInfo info = storageService.getResourceInfo(path);
            return ResponseEntity.ok(info);
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    /**
     * DELETE /resource - Удаление ресурса
     */
    @DeleteMapping("/resource")
    public ResponseEntity<?> deleteResource(@RequestParam String path) {
        try {
            validatePath(path);
            storageService.deleteResource(path);
            return ResponseEntity.noContent().build();
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    /**
     * GET /resource/move - Переименование/перемещение ресурса
     */
    @GetMapping("/resource/move")
    public ResponseEntity<?> moveResource(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            validatePath(from);
            validatePath(to);
            ResourceInfo info = storageService.moveResource(from, to);
            return ResponseEntity.ok(info);
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    /**
     * GET /resource/search - Поиск ресурсов
     */
    @GetMapping("/resource/search")
    public ResponseEntity<?> searchResources(@RequestParam String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Search query is required");
            }
            List<ResourceInfo> results = storageService.searchResources(query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    /**
     * POST /resource - Загрузка файлов
     */
    @PostMapping("/resource")
    public ResponseEntity<?> uploadResource(
            @RequestParam String path,
            @RequestParam("files") MultipartFile[] files) {
        try {
            validatePath(path);
            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().body("No files provided");
            }
            List<ResourceInfo> uploaded = storageService.uploadFiles(path, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    /**
     * GET /directory - Получение содержимого папки
     */
    @GetMapping("/directory")
    public ResponseEntity<?> getDirectoryContents(@RequestParam String path) {
        try {
            validatePath(path);
            List<ResourceInfo> contents = storageService.getDirectoryContents(path);
            return ResponseEntity.ok(contents);
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    /**
     * POST /directory - Создание пустой папки
     */
    @PostMapping("/directory")
    public ResponseEntity<?> createDirectory(@RequestParam String path) {
        try {
            validatePath(path);
            ResourceInfo created = storageService.createDirectory(path);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (StorageService.InvalidPathException e) {
            return ResponseEntity.badRequest().body("Invalid path: " + e.getMessage());
        } catch (StorageService.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (StorageService.ResourceAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unknown error");
        }
    }

    private void validatePath(String path) throws StorageService.InvalidPathException {
        if (pathValidator.validateAndGetType(path) == null) {
            throw new StorageService.InvalidPathException("Invalid or missing path");
        }
    }
}
