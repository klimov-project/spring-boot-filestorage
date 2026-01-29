package com.project.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface MinioService {

    List<MinioObject> listObjects(String userFolder);

    void deleteObject(String path);

    void renameObject(String fromPath, String toPath);

    List<MinioObject> searchFiles(String query);

    List<MinioObject> uploadFiles(String destinationPath, MultipartFile[] files);

    void createFolder(String path);

    String getBucketName();
}
