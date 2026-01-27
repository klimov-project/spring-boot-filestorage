package com.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ResourceIntegrationTests {

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

//     @AfterEach
//     public void cleanup() throws Exception {
//         // Пытаемся удалить все тестовые директории, даже если тест упал
//         try {
//             mockMvc.perform(delete("/api/resource")
//                     .param("path", basePath + "/"))
//                     .andExpect(status().isNoContent());
//         } catch (Exception e) {
//             // Игнорируем ошибки при удалении
//         }
//     }

    // === ТЕСТ 1: Создание директории ===
    @Test
    public void test01_createDirectory() throws Exception {
        String dirPath = basePath + "simple-dir/";

        // Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(basePath + "simple-dir"))
                .andExpect(jsonPath("$.path").value(""))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));

        // Проверяем, что директория существует
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(basePath + "simple-dir"))
                .andExpect(jsonPath("$.path").value(""))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));
    }

    // === ТЕСТ 2: Создание и удаление директории ===
    @Test
    public void test02_createAndDeleteDirectory() throws Exception {
        String dirPath = basePath + "temp-dir/";

        // 1. Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // 2. Проверяем создание
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isOk());

        // 3. Удаляем директорию
        mockMvc.perform(delete("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isNoContent());

        // 4. Проверяем, что директория удалена
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isNotFound());
    }

    // === ТЕСТ 3: Создание файла ===
    @Test
    public void test03_createFile() throws Exception {
        String dirPath = basePath + "upload-dir/";
        String fileName = "test-file.txt";
        String fileContent = "Simple test content"; 

        // 1. Загружаем файл
        mockMvc.perform(multipart("/api/resource")
                .file("files", fileContent.getBytes())
                .param("path", dirPath)
                .param("files", fileName))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].name").value(fileName))
                .andExpect(jsonPath("$[0].path").value(dirPath))
                .andExpect(jsonPath("$[0].type").value("FILE"))
                .andExpect(jsonPath("$[0].size").exists());

        // 2. Проверяем, что файл существует
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath + fileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(fileName))
                .andExpect(jsonPath("$.type").value("FILE"))
                .andExpect(jsonPath("$.size").value(fileContent.length()));
    }

    // === ТЕСТ 4: Создание и удаление файла ===
    @Test
    public void test04_createAndDeleteFile() throws Exception {
        String dirPath = basePath + "file-test/";
        String fileName = "delete-me.txt";
        String fileContent = "Content to be deleted";

        // 1. Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // 2. Загружаем файл
        mockMvc.perform(multipart("/api/resource")
                .file("files", fileContent.getBytes())
                .param("path", dirPath)
                .param("files", fileName))
                .andExpect(status().isCreated());

        // 3. Проверяем создание
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath + fileName))
                .andExpect(status().isOk());

        // 4. Удаляем файл
        mockMvc.perform(delete("/api/resource")
                .param("path", dirPath + fileName))
                .andExpect(status().isNoContent());

        // 5. Проверяем удаление
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath + fileName))
                .andExpect(status().isNotFound());
    }

    // === ТЕСТ 5: Создание и переименование директории ===
    @Test
    public void test05_createAndRenameDirectory() throws Exception {
        String originalDir = basePath + "original-dir/";
        String renamedDir = basePath + "renamed-dir/";

        // 1. Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", originalDir))
                .andExpect(status().isCreated());

        // 2. Проверяем создание
        mockMvc.perform(get("/api/resource")
                .param("path", originalDir))
                .andExpect(status().isOk());

        // 3. Переименовываем директорию
        mockMvc.perform(get("/api/resource/move")
                .param("from", originalDir)
                .param("to", renamedDir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed-dir"))
                .andExpect(jsonPath("$.path").value(basePath))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));

        // 4. Проверяем, что оригинальная директория не существует
        mockMvc.perform(get("/api/resource")
                .param("path", originalDir))
                .andExpect(status().isNotFound());

        // 5. Проверяем, что переименованная директория существует
        mockMvc.perform(get("/api/resource")
                .param("path", renamedDir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed-dir"));
    }

    // === ТЕСТ 6: Создание и переименование файла ===
    @Test
    public void test06_createAndRenameFile() throws Exception {
        String dirPath = basePath + "rename-test/";
        String originalFile = "original.txt";
        String renamedFile = "renamed.txt";
        String fileContent = "File to rename";

        // 1. Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // 2. Загружаем файл
        mockMvc.perform(multipart("/api/resource")
                .file("files", fileContent.getBytes())
                .param("path", dirPath)
                .param("files", originalFile))
                .andExpect(status().isCreated());

        // 3. Переименовываем файл
        mockMvc.perform(get("/api/resource/move")
                .param("from", dirPath + originalFile)
                .param("to", dirPath + renamedFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamedFile))
                .andExpect(jsonPath("$.path").value(dirPath))
                .andExpect(jsonPath("$.type").value("FILE"));

        // 4. Проверяем, что оригинальный файл не существует
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath + originalFile))
                .andExpect(status().isNotFound());

        // 5. Проверяем, что переименованный файл существует
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath + renamedFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamedFile));
    }

    // === ТЕСТ 7: Перемещение файла в другую директорию ===
    @Test
    public void test07_moveFileToDifferentDirectory() throws Exception {
        String sourceDir = basePath + "source/";
        String destDir = basePath + "destination/";
        String fileName = "move-me.txt";
        String fileContent = "File to move";

        // 1. Создаем исходную и целевую директории
        mockMvc.perform(post("/api/directory")
                .param("path", sourceDir))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/directory")
                .param("path", destDir))
                .andExpect(status().isCreated());

        // 2. Загружаем файл в исходную директорию
        mockMvc.perform(multipart("/api/resource")
                .file("files", fileContent.getBytes())
                .param("path", sourceDir)
                .param("files", fileName))
                .andExpect(status().isCreated());

        // 3. Перемещаем файл в целевую директорию
        mockMvc.perform(get("/api/resource/move")
                .param("from", sourceDir + fileName)
                .param("to", destDir + fileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(fileName))
                .andExpect(jsonPath("$.path").value(destDir));

        // 4. Проверяем, что файла нет в исходной директории
        mockMvc.perform(get("/api/directory")
                .param("path", sourceDir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 5. Проверяем, что файл есть в целевой директории
        mockMvc.perform(get("/api/directory")
                .param("path", destDir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(fileName))
                .andExpect(jsonPath("$[0].type").value("FILE"));
    }

    // === ТЕСТ 8: Ошибка 409 при перемещении в существующий файл ===
    @Test
    public void test08_conflictWhenMovingToExistingFile() throws Exception {
        String dirPath = basePath + "conflict-test/";
        String file1 = "file1.txt";
        String file2 = "file2.txt";
        String content1 = "First file";
        String content2 = "Second file";

        // 1. Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // 2. Загружаем два файла
        mockMvc.perform(multipart("/api/resource")
                .file("files", content1.getBytes())
                .param("path", dirPath)
                .param("files", file1))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/resource")
                .file("files", content2.getBytes())
                .param("path", dirPath)
                .param("files", file2))
                .andExpect(status().isCreated());

        // 3. Пытаемся переместить file1 в file2 (должна быть ошибка 409)
        mockMvc.perform(get("/api/resource/move")
                .param("from", dirPath + file1)
                .param("to", dirPath + file2))
                .andExpect(status().isConflict());

        // 4. Проверяем, что оба файла остались на месте
        mockMvc.perform(get("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // === ТЕСТ 9: Получение содержимого директории ===
    @Test
    public void test09_listDirectoryContents() throws Exception {
        String dirPath = basePath + "list-test/";
        String subDir = "subdir/";
        String file1 = "file1.txt";
        String file2 = "file2.txt";

        // 1. Создаем основную директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // 2. Создаем поддиректорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath + subDir))
                .andExpect(status().isCreated());

        // 3. Загружаем два файла
        mockMvc.perform(multipart("/api/resource")
                .file("files", "content1".getBytes())
                .param("path", dirPath)
                .param("files", file1))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/resource")
                .file("files", "content2".getBytes())
                .param("path", dirPath)
                .param("files", file2))
                .andExpect(status().isCreated());

        // 4. Получаем содержимое директории
        mockMvc.perform(get("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)) // 1 папка + 2 файла
                .andExpect(jsonPath("$[?(@.name == 'subdir')].type").value("DIRECTORY"))
                .andExpect(jsonPath("$[?(@.name == 'file1.txt')].type").value("FILE"))
                .andExpect(jsonPath("$[?(@.name == 'file2.txt')].type").value("FILE"));
    }

    // === ТЕСТ 10: Поиск ресурсов ===
    @Test
    public void test10_searchResources() throws Exception {
        String dirPath = basePath + "search-test/";
        String uniqueName = "unique_file_" + testId + ".txt";
        String commonName = "common.txt";

        // 1. Создаем директорию
        mockMvc.perform(post("/api/directory")
                .param("path", dirPath))
                .andExpect(status().isCreated());

        // 2. Загружаем файлы
        mockMvc.perform(multipart("/api/resource")
                .file("files", "unique content".getBytes())
                .param("path", dirPath)
                .param("files", uniqueName))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/resource")
                .file("files", "common content".getBytes())
                .param("path", dirPath)
                .param("files", commonName))
                .andExpect(status().isCreated());

        // 3. Ищем уникальный файл
        mockMvc.perform(get("/api/resource/search")
                .param("query", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(uniqueName));

        // 4. Ищем общий файл (может найти и другие файлы из других тестов)
        mockMvc.perform(get("/api/resource/search")
                .param("query", "common"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'common.txt')].name").value(commonName));
    }
}
