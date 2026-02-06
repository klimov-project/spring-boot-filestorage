package com.project.storage.config;

import com.project.storage.service.MinioStorageService;
import com.project.service.StorageService;
import com.project.service.MinioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public StorageService StorageService(MinioService minioService) {
        return new MinioStorageService(minioService);
    }
}
