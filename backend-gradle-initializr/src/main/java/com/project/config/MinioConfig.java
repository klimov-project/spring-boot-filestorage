package com.project.config;

import io.minio.*;
import io.minio.errors.*;
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

    private String endpoint;

    @Bean
    public MinioClient minioClient() throws Exception {
        System.out.println("=== MinIO Config: Creating client ===");
        endpoint = url + ":" + port;
        System.out.println("MinIO Endpoint: " + endpoint);
        System.out.println("MinIO Access Key: " + accessKey);
        System.out.println("MinIO Bucket: " + bucket);

        MinioClient client = MinioClient.builder()
                .endpoint(endpoint, port, false)
                .credentials(accessKey, secretKey)
                .build();

        // // Проверяем и создаём бакет после инициализации клиента
        initBucket(client);

        System.out.println("=== MinIO Client created successfully ===");
        
        return client;
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
            System.out.println("=== Bucket '" + bucket + "' created successfully ===");
        } else {
            System.out.println("=== Bucket '" + bucket + "' already exists ===");
        }
    }

    @Bean
    public String minioBucket() {
        return bucket;
    }
}
