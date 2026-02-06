package com.project.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MinioService {
    void createFolder(String fullPath);
    List<MinioObject> listObjects(String fullPath);
    List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files);



    String getBucketName();


    void deleteObject(String fullPath);

    void renameObject(String oldFullPath, String newFullPath);

    List<MinioObject> searchFiles(String userFolder, String query);


    String getDownloadUrl(String fullPath);

    boolean objectExists(String fullPath);

    MinioObject getObjectInfo(String fullPath);

}
