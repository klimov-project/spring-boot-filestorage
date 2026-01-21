package com.project.controller;

import com.project.dto.request.SigninRequest;
import com.project.dto.request.SignupRequest;
import com.project.dto.response.ErrorResponse;
import com.project.dto.response.UserResponse;
import com.project.entity.User;
import com.project.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/sign-up Регистрация нового пользователя
     */
    @PostMapping("/sign-up")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        try {
            // Регистрируем пользователя
            User user = authService.registerUser(request);

                // Автоматически аутентифицируем (создаём сессию)
                authService.authenticateUser(request.getUsername(), request.getPassword());

                // Явно сохранить SecurityContext в сессии (для Spring Session/Redis)
                HttpSession session = httpRequest.getSession(true);
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            // Возвращаем ответ 201 Created
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new UserResponse(user.getUsername()));

        } catch (RuntimeException e) {
            // Ошибка 409 если пользователь уже существует
            if (e.getMessage().contains("already exists")) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse("Username занят"));
            }

            // Ошибка 500 для других случаев
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Ошибка при регистрации"));
        }
    }

    /**
     * POST /api/auth/sign-in Вход пользователя
     */
    @PostMapping("/sign-in")
    public ResponseEntity<?> signin(@Valid @RequestBody SigninRequest signinRequest, HttpServletRequest httpRequest) {
        try {
            // Аутентифицируем пользователя (создаём сессию)
            authService.authenticateUser(signinRequest.getUsername(), signinRequest.getPassword());

            // Явно сохранить SecurityContext в сессии (для Spring Session/Redis)
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            // Возвращаем ответ 200 OK
                return ResponseEntity
                    .ok(new UserResponse(signinRequest.getUsername()));

        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            // Ошибка 401 если неверные данные
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Неверные данные"));

        } catch (Exception e) {
            // Ошибка 500 для других случаев
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Ошибка при авторизации"));
        }
    }

    /**
     * POST /api/auth/sign-out Выход пользователя
     */
    @PostMapping("/sign-out")
    public ResponseEntity<?> signout(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Пользователь не авторизован"));
            }

            // Выход обрабатывается Spring Security через SecurityConfig
            // Просто возвращаем 204 No Content
            return ResponseEntity
                    .noContent()
                    .build();

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Ошибка при выходе"));
        }
    }
}
