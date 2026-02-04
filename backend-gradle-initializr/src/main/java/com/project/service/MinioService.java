package com.project.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MinioService {

    String getBucketName();

    List<MinioObject> listObjects(String fullPath);

    void deleteObject(String fullPath);

    void renameObject(String oldFullPath, String newFullPath);

    List<MinioObject> searchFiles(String userFolder, String query);

    List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files);

    void createFolder(String fullPath);

    String getDownloadUrl(String fullPath);

    boolean objectExists(String fullPath);

    MinioObject getObjectInfo(String fullPath);

}
