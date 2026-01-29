package com.project.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${spring.minio.url}")
    private String url;

    @Value("${spring.minio.port}")
    private int port;

    @Value("${spring.minio.access-key}")
    private String accessKey;

    @Value("${spring.minio.secret-key}")
    private String secretKey;

    @Value("${spring.minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        System.out.println("=== MinIO Config: Creating client ===");
        System.out.println("MinIO Endpoint: " + url + ":" + port);
        System.out.println("MinIO Access Key: " + accessKey);
        System.out.println("MinIO Bucket: " + bucket);

        MinioClient client = MinioClient.builder()
                .endpoint(url, port, false)
                .credentials(accessKey, secretKey)
                .build();

        System.out.println("=== MinIO Client created successfully ===");
        return client;
    }

    @Bean
    public String minioBucket() {
        return bucket;
    }
}
