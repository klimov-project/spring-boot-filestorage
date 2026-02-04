package com.project.storage.controller;

import com.project.entity.User;
import com.project.storage.service.DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/download")
@PreAuthorize("isAuthenticated()")
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping
    public ResponseEntity<?> downloadResource(
            @AuthenticationPrincipal User user,
            @RequestParam String path) {

        logger.info("User {} requesting download for: {}", user.getId(), path);

        try {
            DownloadService.DownloadResult result
                    = downloadService.getDownloadResource(user.getId(), path);

            // Настраиваем заголовки
            HttpHeaders headers = new HttpHeaders();
            String filename = result.getFilename();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");

            // Определяем Content-Type
            if (result.isZip()) {
                headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
            } else {
                headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(result.getResource());

        } catch (Exception e) {
            logger.error("Error downloading resource for user {}: {}",
                    user.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error downloading resource: " + e.getMessage());
        }
    }
}
