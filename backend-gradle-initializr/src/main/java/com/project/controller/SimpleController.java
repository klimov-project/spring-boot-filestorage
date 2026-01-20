package com.project.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
public class SimpleController {

    // Главная страница с формой
    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        // Получаем имя пользователя из сессии
        String username = (String) session.getAttribute("username");
        String sessionId = session.getId();

        model.addAttribute("username", username != null ? username : "не авторизован");
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("sessionTime", LocalDateTime.now());

        return "index"; // Будет искать index.html в templates/
    }

    // Вход (упрощённый, без Spring Security)
    @PostMapping("/login-simple") 
    public String loginSimple(@RequestParam String username,
            HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.setAttribute("username", username);
        session.setAttribute("loginTime", LocalDateTime.now());

        return "redirect:/";
    }

    // Выход
    @PostMapping("/logout-simple")
    public String logoutSimple(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }

    // Простая проверка API сессии
    @GetMapping("/api/simple/check")
    public String checkSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        return username != null
                ? "Пользователь: " + username + ", Session ID: " + session.getId()
                : "Сессия пустая";
    }
}
