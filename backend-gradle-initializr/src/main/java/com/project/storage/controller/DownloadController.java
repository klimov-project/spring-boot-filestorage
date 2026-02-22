package com.project.storage.controller;

import com.project.entity.User;
import com.project.storage.service.DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * GET /api/download - Скачивание файла/папки
     */
    @GetMapping("/resource/download")
    public ResponseEntity<?> downloadResource(
            @AuthenticationPrincipal User user,
            @RequestParam(required = true) String path) {

        logger.info("User {} requesting download for: {}", user.getId(), path);
        DownloadService.DownloadResult result
                = downloadService.getDownloadResource(user.getId(), path);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + result.getFilename() + "\"");
        headers.setContentType(result.isZip()
                ? MediaType.valueOf("application/zip")
                : MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.getResource());
    }
}
