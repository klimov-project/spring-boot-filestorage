package com.project.storage.dto;

import com.project.storage.model.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInfo {
    
    /**
     * Относительный путь (без префикса user-{id}-files)
     */
    private String path;
    
    /**
     * Имя ресурса
     */
    private String name;
    
    /**
     * Размер (только для файла, null для папок)
     */
    private Long size;
    
    /**
     * Тип: FILE или DIRECTORY
     */
    private ResourceType type;
    
    /**
     * ID пользователя-владельца
     */
    private Long userId;
    
    /**
     * Дата последнего изменения
     */
    private Instant lastModified;
    
    /**
     * URL для скачивания (генерируется при необходимости)
     */
    private String downloadUrl;
    
    /**
     * MIME тип файла
     */
    private String contentType;
}