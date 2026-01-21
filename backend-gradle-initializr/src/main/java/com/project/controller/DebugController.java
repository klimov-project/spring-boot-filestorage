package com.project.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public DebugController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessionsInfo(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        // Информация о текущей HTTP сессии
        result.put("currentSessionId", session.getId());
        result.put("creationTime", session.getCreationTime());
        result.put("lastAccessedTime", session.getLastAccessedTime());
        result.put("maxInactiveInterval", session.getMaxInactiveInterval());
        
        // Сессии в Redis
        Set<String> sessionKeys = redisTemplate.keys("spring:session:*");
        Set<String> activeSessions = redisTemplate.keys("spring:session:sessions:*");
        
        result.put("totalRedisKeys", sessionKeys != null ? sessionKeys.size() : 0);
        result.put("activeSessions", activeSessions != null ? activeSessions.size() : 0);
        
        // Примеры сессий (первые 3)
        if (activeSessions != null && !activeSessions.isEmpty()) {
            Map<String, Object> sampleSessions = new HashMap<>();
            int count = 0;
            for (String key : activeSessions) {
                if (count >= 3) break;
                
                Long ttl = redisTemplate.getExpire(key);
                sampleSessions.put(key, Map.of(
                    "ttl", ttl != null ? ttl + " секунд" : "нет TTL",
                    "expiresIn", ttl != null && ttl > 0 ? 
                        String.format("%d мин %d сек", ttl / 60, ttl % 60) : "истекла"
                ));
                count++;
            }
            result.put("sampleSessions", sampleSessions);
        }
        
        return ResponseEntity.ok(result);
    }
}