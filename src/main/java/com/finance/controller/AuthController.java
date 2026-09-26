package com.finance.controller;

import com.finance.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@AllArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/auth/register")
    public String registerUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String fullName,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            userService.registerUser(username, email, password, fullName);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful! Please login.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "register";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition or DB unique constraint (username/email) — show friendly message, not 500
            model.addAttribute("errorMessage", "That username or email is already in use. Try logging in instead.");
            return "register";
        }
    }
}
