package com.project.config;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    
    @Bean
    public MinioClient minioClient() {
        System.out.println("=== MinIO Config: Creating client ===");
        try {
            // Жёстко закодированные значения для теста
            MinioClient client = MinioClient.builder()
                    .endpoint("http://localhost:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();
            System.out.println("=== MinIO Client created successfully ===");
            return client;
        } catch (Exception e) {
            System.out.println("=== ERROR creating MinIO client: " + e.getMessage());
            throw new RuntimeException("MinIO initialization failed", e);
        }
    }
}