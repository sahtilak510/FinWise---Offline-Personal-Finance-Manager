package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.excel.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/notifications")
@AllArgsConstructor
public class NotificationsController {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @GetMapping
    public String viewNotifications(Authentication authentication, Model model,
                                  @RequestParam(value = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        notificationService.checkAndGenerateNotifications(user);

        var notifications = notificationService.getUserNotifications(user, unreadOnly);
        var allNotifications = notificationService.getUserNotifications(user, false);
        long unreadCount = allNotifications.stream().filter(n -> !n.isRead()).count();

        model.addAttribute("user", user);
        model.addAttribute("notifications", notifications);
        model.addAttribute("allNotifications", allNotifications);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("unreadOnly", unreadOnly);

        return "notifications";
    }

    @PostMapping("/mark-read/{id}")
    public String markAsRead(Authentication authentication, @PathVariable String id) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        notificationService.markAsRead(user, id);
        return "redirect:/notifications";
    }

    @PostMapping("/mark-all-read")
    public String markAllAsRead(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        notificationService.markAllAsRead(user);
        return "redirect:/notifications";
    }
}
