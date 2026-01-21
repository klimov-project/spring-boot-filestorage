package com.project.controller;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    
    private final ApplicationAvailability availability;
    
    public HealthController(ApplicationAvailability availability) {
        this.availability = availability;
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Spring Boot Application");
        health.put("timestamp", System.currentTimeMillis());
        health.put("livenessState", availability.getLivenessState());
        health.put("readinessState", availability.getReadinessState());
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/health/simple")
    public ResponseEntity<String> simpleHealth() {
        return ResponseEntity.ok("OK");
    }
}