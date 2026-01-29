package com.project.storage.service;

import com.project.entity.MinioObject;
import com.project.service.MinioService;
import com.project.storage.dto.ResourceInfo;
import com.project.storage.model.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MinioStorageService implements StorageService {

    private final MinioService minioService;

    public MinioStorageService(MinioService minioService) {
        this.minioService = minioService;
    }

    @Override
    public ResourceInfo getResourceInfo(String path) throws ResourceNotFoundException {
        if ("/".equals(path)) {
            // Возвращаем специальный объект для корневой директории
            return new ResourceInfo("/", "root", null, ResourceType.DIRECTORY);
        }

        List<MinioObject> objects = minioService.listObjects(path);
        if (objects.isEmpty()) {
            throw new ResourceNotFoundException("Resource not found: " + path);
        }
        return convertToResourceInfo(objects.get(0));
    }

    @Override
    public void deleteResource(String path) throws ResourceNotFoundException {
        minioService.deleteObject(path);
    }

    @Override
    public ResourceInfo moveResource(String fromPath, String toPath)
            throws ResourceNotFoundException, ResourceAlreadyExistsException, InvalidPathException {
        minioService.renameObject(fromPath, toPath);
        return getResourceInfo(toPath);
    }

    @Override
    public List<ResourceInfo> searchResources(String query) {
        return minioService.searchFiles(query).stream()
                .map(this::convertToResourceInfo)
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceInfo> uploadFiles(String destinationPath, MultipartFile[] files)
            throws InvalidPathException, ResourceAlreadyExistsException {
        return minioService.uploadFiles(destinationPath, files).stream()
                .map(this::convertToResourceInfo)
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceInfo> getDirectoryContents(String path) throws ResourceNotFoundException {
        return minioService.listObjects(path).stream()
                .map(this::convertToResourceInfo)
                .collect(Collectors.toList());
    }

    @Override
    public ResourceInfo createDirectory(String path)
            throws InvalidPathException, ResourceNotFoundException, ResourceAlreadyExistsException {
        minioService.createFolder(path);
        return getResourceInfo(path);
    }

    private ResourceInfo convertToResourceInfo(MinioObject minioObject) {
        ResourceType type = minioObject.isDirectory() ? ResourceType.DIRECTORY : ResourceType.FILE;
        return new ResourceInfo(
                minioObject.getPath(),
                minioObject.getName(),
                type == ResourceType.FILE ? minioObject.getSize() : null,
                type
        );
    }
}
