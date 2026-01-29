package com.project.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface MinioService {
    List<MinioObject> listObjects(String path);
    void uploadFile(String destinationPath, MultipartFile file);
    void deleteFile(String filePath); 


    // String getBucketName();

    // List<MinioObject> listObjects(String userFolder);

    // List<MinioObject> uploadFiles(String userDirectory, MultipartFile[] files); 

    // Map<String, MinioObject> search(String userDirectory, String userFolder);
    // boolean createFolder(String folderName);
    // boolean folderExist(String folderName);
    // void deleteFolder(String[] folderName);
    // void renameFile(String filePath, String fileNewName);
    // void renameDirectory(String filePath, String fileName);
}
