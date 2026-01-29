package com.project.entity;

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
    private Long size;  // Изменил тип на Long, чтобы можно было передавать null для папок
    private boolean isDirectory;
}
