package com.project.storage.service;

import com.project.entity.MinioObject;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface MinioService {

    void createFolder(String fullPath); // strict mode by default

    void createFolder(String fullPath, boolean strict); // for "uploadFiles"

    List<MinioObject> listObjects(String fullPath);

    List<MinioObject> uploadFiles(String destinationFullPath, MultipartFile[] files);

    void renameObject(String oldFullPath, String newFullPath);

    List<MinioObject> searchFiles(String rootFullPath, String query);

    String getDownloadUrl(String fullPath);

    boolean isObjectExists(String fullPath) throws Exception;

    MinioObject getObjectInfo(String fullPath);

    void deleteObject(String fullPath);

}
