package com.project.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.project.dto.request.SignupRequest;
import com.project.entity.User;
import com.project.repository.UserRepository;
import com.project.exception.UsernameExistsException;

import com.project.storage.service.StorageService;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final StorageService storageService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            StorageService storageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.storageService = storageService;
    }

    @Transactional(rollbackFor = Exception.class) // Откат при любых исключениях
    public User registerUser(SignupRequest request) {
        // Проверяем, существует ли пользователь
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameExistsException("Username already exists");
        }

        // Создаём нового пользователя
        User user = new User();

        // Создаём корневую папку для пользователя в MinIO
        String userRootFolder = "user-" + user.getId() + "-files/";

        logger.info("userRootFolder: {}", userRootFolder);
        try {
            storageService.createDirectory(userRootFolder);
        } catch (Exception e) {
            // Если не удалось создать папку, выбрасываем исключение для отката транзакции
            throw new RuntimeException("Failed to create user directory in MinIO", e);
        }

        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);
        logger.info("User {} saved to database with ID: {}", request.getUsername(), user.getId());

        return user;
    }

    public void authenticateUser(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
