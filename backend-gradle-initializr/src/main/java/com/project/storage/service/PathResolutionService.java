package com.project.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PathResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(PathResolutionService.class);

    private final Path storageRoot;

    public PathResolutionService(@Value("${storage.root.path:./storage}") String storageRootPath) {
        this.storageRoot = Paths.get(storageRootPath).toAbsolutePath().normalize();
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    public Path resolveUserPath(String userPath) {
        logger.debug("Resolving user path: {}", userPath);

        // Хардкод userID = 1
        final String userID = "1";
        String bucketPrefix = "user-" + userID + "-bucket";

        // Обработка случая, когда userPath равен "/"
        String normalizedUserPath;
        if ("/".equals(userPath)) {
            normalizedUserPath = bucketPrefix;
        } else {
            // Убираем начальный слэш, если он есть, и добавляем префикс
            normalizedUserPath = bucketPrefix + "/" + userPath.replaceAll("^/+", "");
        }

        logger.debug("Normalized user path: {}", normalizedUserPath);

        Path resolved = storageRoot.resolve(normalizedUserPath).normalize();

        // Защита от path traversal за пределы storageRoot
        if (!resolved.startsWith(storageRoot)) {
            String errorMsg = String.format(
                    "Access denied: path traversal attempt. User path: %s, Resolved: %s",
                    userPath, resolved
            );
            logger.warn(errorMsg);
            throw new SecurityException(errorMsg);
        }

        logger.debug("Resolved to physical path: {}", resolved);
        return resolved;
    }
}
