package com.project.entity;

import java.time.Instant;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class MinioObject {

    private String name;
    private String path;
    private Long size;
    private boolean isDirectory;
    private Instant lastModified;
}
