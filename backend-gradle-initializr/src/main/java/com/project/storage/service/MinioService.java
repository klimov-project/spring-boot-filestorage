package com.project.storage.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface MinioService {

    void createFolder(String fullPath);

    List<MinioObject> listObjects(String fullPath);

    List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files);
 

    void renameObject(String oldFullPath, String newFullPath);

    List<MinioObject> searchFiles(String rootFullPath, String query);

    String getDownloadUrl(String fullPath);

    boolean objectExists(String fullPath);

    MinioObject getObjectInfo(String fullPath);

    void deleteObject(String fullPath);

}
