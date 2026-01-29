package com.project.storage.dto;

import com.project.storage.model.ResourceType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceResponse {

    private String path; // Путь к папке, в которой лежит ресурс
    private String name;
    private Long size; // Только для файлов
    private ResourceType type;

    // Конструкторы
    public ResourceResponse() {
    }

    public ResourceResponse(String path, String name, Long size, ResourceType type) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public ResourceResponse(String path, String name, ResourceType type) {
        this.path = path;
        this.name = name;
        this.type = type;
        // size остается null для папок
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
