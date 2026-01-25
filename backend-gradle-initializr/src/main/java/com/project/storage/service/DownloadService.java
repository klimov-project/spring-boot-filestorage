package com.project.storage.service;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface DownloadService {

    /**
     * Получение ресурса для скачивания
     */
    DownloadResult getDownloadResource(String path)
            throws StorageService.ResourceNotFoundException, StorageService.InvalidPathException, IOException;

    /**
     * Результат скачивания
     */
    class DownloadResult {

        private final Resource resource;
        private final String filename;
        private final boolean isZip; // true если это zip-архив папки

        public DownloadResult(Resource resource, String filename, boolean isZip) {
            this.resource = resource;
            this.filename = filename;
            this.isZip = isZip;
        }

        public Resource getResource() {
            return resource;
        }

        public String getFilename() {
            return filename;
        }

        public boolean isZip() {
            return isZip;
        }
    }
}
