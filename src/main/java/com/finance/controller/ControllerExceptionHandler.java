package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.excel.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ControllerExceptionHandler {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ControllerExceptionHandler(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @ModelAttribute("unreadCount")
    public Long unreadCount(Authentication authentication, HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) || authentication == null
                || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> (long) notificationService.getUserNotifications(user, true).size())
                .orElse(null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException exception, Model model) {
        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("statusCode", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("error", "Bad Request");
        model.addAttribute("errorTitle", "Invalid request");
        model.addAttribute("message", exception.getMessage());
        model.addAttribute("errorMessage", exception.getMessage());
        model.addAttribute("errorDescription", "Check the requested record and try again.");
        model.addAttribute("requestPath", "");
        return "error/error";
    }
}
