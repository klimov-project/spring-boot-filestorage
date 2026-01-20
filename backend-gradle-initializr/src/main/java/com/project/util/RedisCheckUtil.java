package com.project.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RedisCheckUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCheckUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String checkRedisSessions() {
        try {
            // Ищем все ключи сессий Spring
            Set<String> keys = redisTemplate.keys("spring:session:*");

            StringBuilder result = new StringBuilder();
            result.append("Найдено ключей сессий: ").append(keys != null ? keys.size() : 0).append("\n");

            if (keys != null && !keys.isEmpty()) {
                result.append("Ключи:\n");
                for (String key : keys) {
                    result.append("- ").append(key).append("\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            return "Ошибка при проверке Redis: " + e.getMessage();
        }
    }
}
