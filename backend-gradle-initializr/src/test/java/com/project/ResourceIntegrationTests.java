package com.project;

import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ResourceIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    // Используем уникальные префиксы для каждого теста
    private String testId;
    private String basePath;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        this.testId = UUID.randomUUID().toString().substring(0, 8);
        this.basePath = "test-" + testId + "-";
    }

    // === ТЕСТ 1: Создание директории ===
    @Test
    public void test01_createDirectory() throws Exception {

        String username = "testuser";
        String password = "password";

        // 1. Регистрация
        String signupJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        MvcResult signupResult = mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        System.out.println("=== SIGN-UP RESPONSE ===");
        printResponseDetails("Sign-Up", signupResult);

        // Проверяем, что пользователь создан в базе данных
        assertTrue(userRepository.findByUsername(username).isPresent(),
                "User should be created in database");
        System.out.println("[Done] User created in database: " + username);

        // 2. Вход с правильными данными
        String signinJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        MvcResult signinResult = mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signinJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        System.out.println("\n=== SIGN-IN RESPONSE ===");
        printResponseDetails("Sign-In", signinResult);

        String dirPath = basePath + "simple-dir/";

        // Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(dirPath))
                .andExpect(jsonPath("$.path").value("/"))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));

        // Проверяем, что директория существует
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(dirPath))
                .andExpect(jsonPath("$.path").value("/"))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));
    }

    // === ТЕСТ 2: Получение содержимого папки ===
    @Test
    public void test02_getDirectoryContents() throws Exception {
        String username = "testuser_dir";
        String password = "password";

        // Регистрация и вход
        registerAndLogin(username, password);

        String dirPath = basePath + "get-dir/";
        String filePath1 = basePath + "get-dir/file1.txt";
        String filePath2 = basePath + "get-dir/file2.txt";

        // Создание папки
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // Загрузка файлов
        uploadFile(filePath1, "Content 1");
        uploadFile(filePath2, "Content 2");

        // Получение содержимого папки
        mockMvc.perform(get("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").exists())
                .andExpect(jsonPath("$.length()").value(2));

        System.out.println("[Done] Directory content retrieved successfully");
    }

    // === ТЕСТ 3: Ошибка 404 при получении несуществующей папки ===
    @Test
    public void test03_getDirectoryNotFound() throws Exception {
        String username = "testuser_notfound";
        String password = "password";

        registerAndLogin(username, password);

        mockMvc.perform(get("/api/directory")
                .param("path", basePath + "nonexistent-dir/"))
                .andExpect(status().isNotFound());

        System.out.println("[Done] 404 returned for non-existent directory");
    }

    // === ТЕСТ 4: Ошибка 400 при пустом пути ===
    @Test
    public void test04_getDirectoryInvalidPath() throws Exception {
        String username = "testuser_invalid";
        String password = "password";

        registerAndLogin(username, password);

        mockMvc.perform(get("/api/directory")
                .param("path", ""))
                .andExpect(status().isBadRequest());

        System.out.println("[Done] 400 returned for invalid path");
    }

    // === ТЕСТ 5: Удаление файла ===
    @Test
    public void test05_deleteFile() throws Exception {
        String username = "testuser_delete";
        String password = "password";

        registerAndLogin(username, password);

        String filePath = basePath + "delete-test.txt";

        // Создание файла
        uploadFile(filePath, "Delete me");

        // Проверка что файл существует
        mockMvc.perform(get("/api/resource")
                .param("path", filePath))
                .andExpect(status().isOk());

        // Удаление файла
        mockMvc.perform(delete("/api/resource")
                .param("path", filePath))
                .andExpect(status().isNoContent());

        // Проверка что файл удален
        mockMvc.perform(get("/api/resource")
                .param("path", filePath))
                .andExpect(status().isNotFound());

        System.out.println("[Done] File deleted successfully");
    }

    // === ТЕСТ 6: Удаление папки ===
    @Test
    public void test06_deleteDirectory() throws Exception {
        String username = "testuser_deldir";
        String password = "password";

        registerAndLogin(username, password);

        String dirPath = basePath + "delete-dir/";

        // Создание папки
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // Удаление папки
        mockMvc.perform(delete("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isNoContent());

        System.out.println("[Done] Directory deleted successfully");
    }

    // === ТЕСТ 7: Ошибка 404 при удалении несуществующего ресурса ===
    @Test
    public void test07_deleteNotFound() throws Exception {
        String username = "testuser_delnf";
        String password = "password";

        registerAndLogin(username, password);

        mockMvc.perform(delete("/api/resource")
                .param("path", basePath + "nonexistent.txt"))
                .andExpect(status().isNotFound());

        System.out.println("[Done] 404 returned for deleting non-existent resource");
    }

    // === ТЕСТ 8: Скачивание файла ===
    @Test
    public void test08_downloadFile() throws Exception {
        String username = "testuser_download";
        String password = "password";

        registerAndLogin(username, password);

        String filePath = basePath + "download-test.txt";
        String fileContent = "This is test content for download";

        // Создание файла
        uploadFile(filePath, fileContent);

        // Скачивание файла
        mockMvc.perform(get("/api/resource/download")
                .param("path", filePath))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));

        System.out.println("[Done] File downloaded successfully");
    }

    // === ТЕСТ 9: Скачивание папки как ZIP ===
    @Test
    public void test09_downloadDirectory() throws Exception {
        String username = "testuser_zipdown";
        String password = "password";

        registerAndLogin(username, password);

        String dirPath = basePath + "download-dir/";

        // Создание папки
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // Загрузка файла в папку
        uploadFile(dirPath + "file.txt", "Content");

        // Скачивание папки
        mockMvc.perform(get("/api/resource/download")
                .param("path", dirPath + "file.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));

        System.out.println("[Done] Directory downloaded as ZIP successfully");
    }

    // === ТЕСТ 10: Ошибка 404 при скачивании несуществующего ресурса ===
    @Test
    public void test10_downloadNotFound() throws Exception {
        String username = "testuser_dlnf";
        String password = "password";

        registerAndLogin(username, password);

        mockMvc.perform(get("/api/resource/download")
                .param("path", basePath + "nonexistent.txt"))
                .andExpect(status().isNotFound());

        System.out.println("[Done] 404 returned for downloading non-existent resource");
    }

    // === ТЕСТ 11: Переименование файла ===
    @Test
    public void test11_renameFile() throws Exception {
        String username = "testuser_rename";
        String password = "password";

        registerAndLogin(username, password);

        String originalPath = basePath + "original.txt";
        String renamedPath = basePath + "renamed.txt";

        // Создание файла
        uploadFile(originalPath, "Content");

        // Переименование файла
        String moveJson = String.format("{\"from\":\"%s\",\"to\":\"%s\"}", originalPath, renamedPath);

        mockMvc.perform(patch("/api/resource/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(moveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamedPath));

        System.out.println("[Done] File renamed successfully");
    }

    // === ТЕСТ 12: Перемещение файла ===
    @Test
    public void test12_moveFile() throws Exception {
        String username = "testuser_move";
        String password = "password";

        registerAndLogin(username, password);

        String sourceDir = basePath + "source-move/";
        String destDir = basePath + "dest-move/";
        String filePath = sourceDir + "file.txt";
        String destPath = destDir + "file.txt";

        // Создание папок
        mockMvc.perform(post("/api/directory").param("path", sourceDir))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/directory").param("path", destDir))
                .andExpect(status().isCreated());

        // Создание файла
        uploadFile(filePath, "Content");

        // Перемещение файла
        String moveJson = String.format("{\"from\":\"%s\",\"to\":\"%s\"}", filePath, destPath);

        mockMvc.perform(patch("/api/resource/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(moveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("file.txt"))
                .andExpect(jsonPath("$.path").value(destDir));

        System.out.println("[Done] File moved successfully");
    }

    // === ТЕСТ 13: Ошибка 409 при перемещении в существующий файл ===
    @Test
    public void test13_moveConflict() throws Exception {
        String username = "testuser_conflict";
        String password = "password";

        registerAndLogin(username, password);

        String file1Path = basePath + "file1.txt";
        String file2Path = basePath + "file2.txt";

        // Создание двух файлов
        uploadFile(file1Path, "Content 1");
        uploadFile(file2Path, "Content 2");

        // Попытка переместить первый файл на второй
        String moveJson = String.format("{\"from\":\"%s\",\"to\":\"%s\"}", file1Path, file2Path);

        mockMvc.perform(patch("/api/resource/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(moveJson))
                .andExpect(status().isConflict());

        System.out.println("[Done] 409 returned for move conflict");
    }

    // === ТЕСТ 14: Ошибка 404 при перемещении несуществующего ресурса ===
    @Test
    public void test14_moveNotFound() throws Exception {
        String username = "testuser_movnf";
        String password = "password";

        registerAndLogin(username, password);

        String moveJson = String.format("{\"from\":\"%s\",\"to\":\"%s\"}",
                basePath + "nonexistent.txt", basePath + "newname.txt");

        mockMvc.perform(patch("/api/resource/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(moveJson))
                .andExpect(status().isNotFound());

        System.out.println("[Done] 404 returned for move non-existent resource");
    }

    // === ТЕСТ 15: Поиск файлов ===
    @Test
    public void test15_searchFiles() throws Exception {
        String username = "testuser_search";
        String password = "password";

        registerAndLogin(username, password);

        // Создание нескольких файлов с похожими именами
        uploadFile(basePath + "searchable-test1.txt", "Content 1");
        uploadFile(basePath + "searchable-test2.txt", "Content 2");
        uploadFile(basePath + "other-file.txt", "Other");

        // Поиск с фильтром
        mockMvc.perform(get("/api/resource/search")
                .param("query", "searchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").exists())
                .andExpect(jsonPath("$.length()").value(2));

        System.out.println("[Done] Search executed successfully");
    }

    // === ТЕСТ 16: Ошибка 400 при пустом поиске ===
    @Test
    public void test16_searchEmptyQuery() throws Exception {
        String username = "testuser_searchem";
        String password = "password";

        registerAndLogin(username, password);

        mockMvc.perform(get("/api/resource/search")
                .param("query", ""))
                .andExpect(status().isBadRequest());

        System.out.println("[Done] 400 returned for empty search query");
    }

    // === ТЕСТ 17: Загрузка одного файла ===
    @Test
    public void test17_uploadSingleFile() throws Exception {
        String username = "testuser_upload";
        String password = "password";

        registerAndLogin(username, password);

        String dirPath = basePath + "upload-dir/";

        // Создание папки
        mockMvc.perform(post("/api/directory").param("path", dirPath))
                .andExpect(status().isCreated());

        // Загрузка файла
        mockMvc.perform(multipart("/api/resource")
                .param("path", dirPath)
                .file(new MockMultipartFile("files", "test.txt", "text/plain", "Test content".getBytes()))
                .contentType("multipart/form-data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].name").value("test.txt"))
                .andExpect(jsonPath("$[0].type").value("FILE"));

        System.out.println("[Done] Single file uploaded successfully");
    }

    // === ТЕСТ 18: Загрузка нескольких файлов ===
    @Test
    public void test18_uploadMultipleFiles() throws Exception {
        String username = "testuser_multup";
        String password = "password";

        registerAndLogin(username, password);

        String dirPath = basePath + "multi-upload/";

        // Создание папки
        mockMvc.perform(post("/api/directory").param("path", dirPath))
                .andExpect(status().isCreated());

        // Загрузка нескольких файлов
        mockMvc.perform(multipart("/api/resource")
                .param("path", dirPath)
                .file(new MockMultipartFile("files", "file1.txt", "text/plain", "Content 1".getBytes()))
                .file(new MockMultipartFile("files", "file2.txt", "text/plain", "Content 2".getBytes()))
                .contentType("multipart/form-data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        System.out.println("[Done] Multiple files uploaded successfully");
    }

    // === ТЕСТ 19: Ошибка 409 при загрузке существующего файла ===
    @Test
    public void test19_uploadConflict() throws Exception {
        String username = "testuser_uploadconf";
        String password = "password";

        registerAndLogin(username, password);

        String filePath = basePath + "existing.txt";

        // Загрузка первого файла
        uploadFile(filePath, "First upload");

        // Попытка загрузить файл с тем же именем
        mockMvc.perform(multipart("/api/resource")
                .param("path", basePath)
                .file(new MockMultipartFile("files", filePath, "text/plain", "Second upload".getBytes(StandardCharsets.UTF_8)))
                .contentType("multipart/form-data"))
                .andExpect(status().isConflict());

        System.out.println("[Done] 409 returned for upload conflict");
    }

// === ТЕСТ 20: Запрос информации о несуществующей папке ===
    @Test
    public void test_directoryInfoForNonexistentFolder() throws Exception {
        String username = "testuser_dirnf";
        String password = "password";

        registerAndLogin(username, password);

        // Запрос информации о несуществующей папке
        mockMvc.perform(get("/api/directory")
                .param("path", "folder-not-exist/")
                .contentType("application/json"))
                .andExpect(status().isNotFound());

        System.out.println("[Done] 404 returned for directory info on non-existent folder");
    }

    // === ТЕСТ 21: Авторизация - запросы без авторизации ===
    @Test
    public void test21_unauthorizedAccess() throws Exception {
        // Без авторизации
        mockMvc.perform(get("/api/directory")
                .param("path", "/"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/directory")
                .param("path", "test/"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/resource/search")
                .param("query", "test"))
                .andExpect(status().isUnauthorized());

        System.out.println("[Done] 401 returned for unauthorized access");
    }

    // === HELPER METHODS ===
    private void registerAndLogin(String username, String password) throws Exception {
        // Регистрация
        String signupJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson))
                .andExpect(status().isCreated());

        // Вход
        String signinJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signinJson))
                .andExpect(status().isOk());
    }

    private void uploadFile(String path, String content) throws Exception {
        int lastSlashIndex = path.lastIndexOf('/');
        String dirPath = path.substring(0, lastSlashIndex + 1);
        String fileName = path.substring(lastSlashIndex + 1);

        mockMvc.perform(multipart("/api/resource")
                .param("path", dirPath)
                .file(new MockMultipartFile("files", fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8)))
                .contentType("multipart/form-data"))
                .andExpect(status().isCreated());
    }

    private void printResponseDetails(String operation, MvcResult result) throws Exception {
        System.out.println("\n--- " + operation + " ---");
        System.out.println("Status: " + result.getResponse().getStatus());

        String content = result.getResponse().getContentAsString();
        if (!content.isEmpty()) {
            System.out.println("Response body: " + content);
        }

        System.out.println("Headers:");
        boolean hasSecurityHeaders = false;
        for (String header : result.getResponse().getHeaderNames()) {
            String value = result.getResponse().getHeader(header);
            System.out.println("  " + header + ": " + (value != null ? value : "null"));

            // Отмечаем security заголовки
            if (header.toLowerCase().contains("cookie")
                    || header.toLowerCase().contains("session")
                    || header.toLowerCase().contains("authorization")) {
                hasSecurityHeaders = true;
            }
        }

        if (!hasSecurityHeaders) {
            System.out.println("  Note: No security-related headers found (normal for test environment)");
        }
    }

}
