package com.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
public class MoveResourceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMoveFile() throws Exception {
        // 1. Создаем тестовую папку
        mockMvc.perform(post("/api/directory")
                .param("path", "test-move/"))
                .andExpect(status().isCreated());

        // 2. Загружаем тестовый файл
        String fileContent = "Test content for move operation";
        mockMvc.perform(multipart("/api/resource")
                .file("files", fileContent.getBytes())
                .param("path", "test-move/")
                .param("files", "original.txt"))
                .andExpect(status().isCreated());

        // 3. Переименовываем файл (rename only)
        mockMvc.perform(get("/api/resource/move")
                .param("from", "test-move/original.txt")
                .param("to", "test-move/renamed.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed.txt"))
                .andExpect(jsonPath("$.path").value("test-move/"))
                .andExpect(jsonPath("$.type").value("FILE"));

        // 4. Создаем целевую папку для перемещения
        mockMvc.perform(post("/api/directory")
                .param("path", "test-move/destination/"))
                .andExpect(status().isCreated());

        // 5. Перемещаем файл в другую папку (move only)
        mockMvc.perform(get("/api/resource/move")
                .param("from", "test-move/renamed.txt")
                .param("to", "test-move/destination/renamed.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed.txt"))
                .andExpect(jsonPath("$.path").value("test-move/destination/"))
                .andExpect(jsonPath("$.type").value("FILE"));

        // 6. Пробуем переместить в существующий файл (должна быть 409)
        mockMvc.perform(multipart("/api/resource")
                .file("files", "conflict.txt".getBytes())
                .param("path", "test-move/"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/resource/move")
                .param("from", "test-move/conflict.txt")
                .param("to", "test-move/destination/renamed.txt"))
                .andExpect(status().isConflict());

        // 7. Очистка
        mockMvc.perform(delete("/api/resource")
                .param("path", "test-move/"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testMoveDirectory() throws Exception {
        // 1. Создаем структуру папок
        mockMvc.perform(post("/api/directory")
                .param("path", "source-folder/"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/directory")
                .param("path", "source-folder/subfolder/"))
                .andExpect(status().isCreated());

        // 2. Переименовываем папку
        mockMvc.perform(get("/api/resource/move")
                .param("from", "source-folder/")
                .param("to", "renamed-folder/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed-folder"))
                .andExpect(jsonPath("$.path").value(""))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));

        // 3. Проверяем, что подпапка сохранилась
        mockMvc.perform(get("/api/directory")
                .param("path", "renamed-folder/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("subfolder"))
                .andExpect(jsonPath("$[0].type").value("DIRECTORY"));

        // 4. Очистка
        mockMvc.perform(delete("/api/resource")
                .param("path", "renamed-folder/"))
                .andExpect(status().isNoContent());
    }
}
