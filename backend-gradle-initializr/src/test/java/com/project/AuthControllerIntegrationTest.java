package com.project;

import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for authentication endpoints.
 */
@SpringBootTest
public class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()) // Включаем Security
                .build();
        userRepository.deleteAll();
    }

    @Test
    public void signUp_and_signIn_success() throws Exception {
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

        // 3. Вход с неправильным паролем
        String wrongPasswordJson = String.format("{\"username\":\"%s\",\"password\":\"wrongpassword\"}", username);

        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongPasswordJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверные данные"));

        System.out.println("\n[Done] Wrong password correctly rejected");

        // 4. Попытка входа с несуществующим пользователем
        String nonExistentUserJson = "{\"username\":\"nonexistent\",\"password\":\"password\"}";

        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonExistentUserJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверные данные"));

        System.out.println("[Done] Non-existent user correctly rejected");
    }

    @Test
    public void signUp_duplicateUsername_returnsConflict() throws Exception {
        String username = "duplicateuser";
        String password = "password";
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        // Первая регистрация - успех
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));

        System.out.println("[Done] First registration successful");

        // Вторая регистрация - конфликт
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username занят"));

        System.out.println("[Done] Duplicate registration correctly rejected with 409 Conflict");
    }

    @Test
    public void signUp_invalidData_returnsBadRequest() throws Exception {
        // Пустое имя пользователя
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());

        System.out.println("[Done] Empty username rejected");

        // Пустой пароль
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        System.out.println("[Done] Empty password rejected");

        // Нет тела запроса
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        System.out.println("[Done] Empty request body rejected");
    }

    @Test
    public void signIn_invalidData_returnsBadRequest() throws Exception {
        // Пустое имя пользователя
        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());

        // Пустой пароль
        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Нет тела запроса
        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        System.out.println("[Done] Invalid sign-in data correctly rejected");
    }

    @Test
    public void multipleUsers_canRegister_and_signIn() throws Exception {
        // Регистрируем нескольких пользователей
        for (int i = 1; i <= 3; i++) {
            String username = "user" + i;
            String password = "password" + i;
            String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

            mockMvc.perform(post("/api/auth/sign-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(username));

            System.out.println("[Done] Registered user: " + username);
        }

        // Проверяем, что все пользователи созданы
        assertEquals(3, userRepository.count(), "Should have 3 users in database");
        System.out.println("[Done] All 3 users created in database");

        // Каждый пользователь может войти со своими данными
        for (int i = 1; i <= 3; i++) {
            String username = "user" + i;
            String password = "password" + i;
            String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

            mockMvc.perform(post("/api/auth/sign-in")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(username));

            System.out.println("[Done] User can sign in: " + username);
        }
    }

    @Test
    public void user_canRegister_withSpecialCharacters() throws Exception {
        // Тестируем различные допустимые имена пользователей
        String[] testUsernames = {
            "user_name",
            "user.name",
            "user-name",
            "user123",
            "USER",
            "User123_Test"
        };

        for (String username : testUsernames) {
            String json = String.format("{\"username\":\"%s\",\"password\":\"password123\"}", username);

            mockMvc.perform(post("/api/auth/sign-up")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(username));

            System.out.println("[Done] Registered with username: " + username);

            // Очищаем для следующего теста
            userRepository.deleteAll();
        }
    }

    @Test
    public void password_isHashed_inDatabase() throws Exception {
        String username = "hashuser";
        String password = "plainpassword";
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        // Регистрируем пользователя
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());

        // Получаем пользователя из базы
        var userOptional = userRepository.findByUsername(username);
        assertTrue(userOptional.isPresent(), "User should exist in database");

        var user = userOptional.get();
        String storedPassword = user.getPassword();

        // Проверяем, что пароль хэширован (не равен plaintext)
        assertNotEquals(password, storedPassword, "Password should be hashed in database");
        assertTrue(storedPassword.length() > 20, "Hashed password should be longer than plaintext");

        System.out.println("[Done] Password is hashed in database");
        System.out.println("  Plain: " + password);
        System.out.println("  Hashed: " + storedPassword.substring(0, 20) + "...");
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
