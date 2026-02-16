package com.project.storage.controller;

import com.project.entity.User;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.service.StorageService;

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

    public ResourceController(StorageService StorageService) {
        this.StorageService = StorageService;
    }

    /**
     * GET /api/resource - Получение информации о файле/папке
     */
    @GetMapping("/resource")
    public ResponseEntity<?> getResourceInfo(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested /resource with path: {}", user.getId(), path);

        ResourceInfo info = StorageService.getResourceInfo(user.getId(), path);

        logger.info("Resource info retrieved successfully for user {}: {}",
                user.getId(), path);
        return ResponseEntity.ok(info);
    }

    /**
     * DELETE /api/resource - Удаление ресурса
     */
    @DeleteMapping("/resource")
    public ResponseEntity<?> deleteResource(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested DELETE /resource with path: {}", user.getId(), path);

        StorageService.deleteResource(user.getId(), path);

        logger.info("User {} successfully deleted resource: {}",
                user.getId(), path);
        return ResponseEntity.noContent().build();
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

        ResourceInfo movedResource = StorageService.moveResource(user.getId(), from, to);

        logger.info("User {} successfully moved resource from {} to {}",
                user.getId(), from, to);
        return ResponseEntity.ok(movedResource);

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
        List<ResourceInfo> results = StorageService.searchResources(user.getId(), query);

        logger.info("User {} found {} results for query: {}",
                user.getId(), results.size(), query);
        return ResponseEntity.ok(results);

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

        List<ResourceInfo> uploaded = StorageService.uploadFiles(user.getId(), path, files);

        logger.info("User {} successfully uploaded {} files to path: {}",
                user.getId(), uploaded.size(), path);

        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    /**
     * GET /api/directory - Получение содержимого папки
     */
    @GetMapping("/directory")
    public ResponseEntity<?> getDirectoryContents(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requested GET /directory with path: {}", user.getId(), path);
        List<ResourceInfo> contents = StorageService.getDirectoryContents(user.getId(), path);

        logger.info("User {} retrieved {} items from directory: {}",
                user.getId(), contents.size(), path);
        return ResponseEntity.ok(contents);
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

}
