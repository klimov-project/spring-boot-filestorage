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
    
    private String path;
    private String name;
    private Long size;
    private ResourceType type;
    private Long userId;
    private Instant lastModified;
    private String downloadUrl;
    private String contentType;
}