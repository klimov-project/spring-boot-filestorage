package com.project.config;

import io.minio.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.minio")
public class MinioConfig {

    private static final Logger logger = LoggerFactory.getLogger(MinioConfig.class);

    private String url;
    private int port;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        try {
            logger.info("=== Initializing MinIO Client ===");
            logger.info("URL: {}", url);
            logger.info("Port: {}", port);
            logger.info("Access Key: {}", accessKey != null ? "***" : "null");
            logger.info("Bucket: {}", bucket);
            
            
            MinioClient client = MinioClient.builder()
                    .endpoint(url, port, false) // false = не использовать HTTPS
                    .credentials(accessKey, secretKey)
                    .build();
            
            logger.info("MinIO Client created successfully");
            
            // Инициализируем бакет
            initBucket(client);
            
            return client;
            
        } catch (Exception e) {
            logger.error("Failed to initialize MinIO client", e);
            throw new RuntimeException("MinIO initialization failed", e);
        }
    }

    private void initBucket(MinioClient client) throws Exception {
        boolean found = client.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build()
        );

        if (!found) {
            client.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            logger.info("Bucket '{}' created successfully", bucket);
        } else {
            logger.info("Bucket '{}' already exists", bucket);
        }
    }
}