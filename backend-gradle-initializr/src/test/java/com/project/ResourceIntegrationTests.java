package com.project;

import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.web.servlet.MvcResult;

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
                .andExpect(jsonPath("$.name").value(basePath + "simple-dir"))
                .andExpect(jsonPath("$.path").value("/"))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));

        // Проверяем, что директория существует
        mockMvc.perform(get("/api/resource")
                .param("path", dirPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(basePath + "simple-dir"))
                .andExpect(jsonPath("$.path").value("/"))
                .andExpect(jsonPath("$.type").value("DIRECTORY"));
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
