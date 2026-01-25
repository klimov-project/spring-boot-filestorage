// File: src/main/java/com/project/storage/model/Resource.java
package com.project.storage.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resources")
public class Resource {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String path; // Путь к папке, в которой лежит ресурс (оканчивается на / для папок)
    
    @Column(nullable = false)
    private String name; // Имя ресурса
    
    private Long size; // Размер в байтах (null для папок)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;
    
    @Column(name = "user_id", nullable = false)
    private Long userId; // ID владельца ресурса
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Конструкторы
    public Resource() {}
    
    public Resource(String path, String name, Long size, ResourceType type, Long userId) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.type = type;
        this.userId = userId;
    }
    
    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    
    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // Вспомогательный метод для получения полного пути
    public String getFullPath() {
        return path + name + (type == ResourceType.DIRECTORY ? "/" : "");
    }
}