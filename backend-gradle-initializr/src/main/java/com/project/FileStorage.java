package com.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableRedisHttpSession  // Включаем поддержку Redis сессий
public class FileStorage {

    public static void main(String[] args) {
        SpringApplication.run(FileStorage.class, args);
    }
}