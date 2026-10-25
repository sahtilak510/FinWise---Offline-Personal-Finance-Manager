package com.finance.controller;

import com.finance.service.AdminPasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {
    private final AdminPasswordResetService passwordResetService;

    public PasswordResetController(AdminPasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) String token,
                            Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) Long userId,
                                @RequestParam(required = false) String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "The passwords do not match.");
            model.addAttribute("userId", userId);
            model.addAttribute("token", token);
            return "reset-password";
        }
        try {
            passwordResetService.resetPassword(userId, token, newPassword);
            return "redirect:/login?reset=success";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("userId", userId);
            model.addAttribute("token", token);
            return "reset-password";
        }
    }
}
