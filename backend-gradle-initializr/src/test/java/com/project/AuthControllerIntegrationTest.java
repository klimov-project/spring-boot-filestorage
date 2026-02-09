package com.project;

import com.project.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests for authentication endpoints.
 *
 * Test cases covered: 1) Duplicate registration -> 409 Conflict 2) Sign-in with
 * wrong password -> 401 Unauthorized 3) Successful sign-up -> 201 Created and
 * session cookie is set
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
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        userRepository.deleteAll();
    }

    @Test
    public void signUp_success_createsSessionAndReturns201() throws Exception {
        String json = "{\"username\":\"user_1\",\"password\":\"password\"}";

        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user_1"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    public void signUp_duplicateUsername_returns409() throws Exception {
        String json = "{\"username\":\"user_dup\",\"password\":\"password\"}";

        // First registration should succeed
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());

        // Second registration with same username should return 409
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username занят"));
    }

    @Test
    public void signIn_wrongPassword_returns401() throws Exception {
        String signup = "{\"username\":\"user_login\",\"password\":\"correctpass\"}";
        String badSignin = "{\"username\":\"user_login\",\"password\":\"wrongpass\"}";

        // Create user
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signup))
                .andExpect(status().isCreated());

        // Attempt sign-in with wrong password
        mockMvc.perform(post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(badSignin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверные данные"));
    }
}
