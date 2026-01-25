package com.project.storage.dto;

import com.project.storage.model.ResourceType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL) // Поле size будет исключено, если null
public class ResourceInfo {

    private String path; // Путь к родительской папке
    private String name; // Имя ресурса
    private Long size;   // Размер (только для файла)
    private ResourceType type; // Тип: FILE или DIRECTORY  

    public ResourceInfo(String path, String name, Long size, ResourceType type) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    // Геттеры и сеттеры
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }
}
