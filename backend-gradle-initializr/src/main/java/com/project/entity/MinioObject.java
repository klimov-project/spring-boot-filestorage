package com.project.entity;
import jakarta.persistence.*;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
@Entity
public class MinioObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String path;
    private boolean isDirectory;

    public static MinioObject builder(String objectName, String objectName1, boolean isDir) {
        return MinioObject.builder()
                .name(objectName)
                .path(objectName1)
                .isDirectory(isDir)
                .build();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
