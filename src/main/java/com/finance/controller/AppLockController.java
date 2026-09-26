package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.AppLockService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app-lock")
public class AppLockController {

    private final UserRepository userRepository;
    private final AppLockService appLockService;

    public AppLockController(UserRepository userRepository, AppLockService appLockService) {
        this.userRepository = userRepository;
        this.appLockService = appLockService;
    }

    @GetMapping
    public String viewLock(Authentication authentication, Model model, HttpSession session) {
        User user = currentUser(authentication);
        if (!appLockService.isEnabled(user)) {
            return "redirect:/settings";
        }
        if (appLockService.isUnlocked(user, session)) {
            return "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        return "app-lock";
    }

    @PostMapping("/unlock")
    public String unlock(Authentication authentication, @RequestParam String passcode,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (appLockService.verify(user, passcode)) {
            appLockService.unlock(user, session);
            return "redirect:/dashboard";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "Incorrect passcode.");
        return "redirect:/app-lock";
    }

    @PostMapping("/lock")
    public String lock(Authentication authentication, HttpSession session) {
        User user = currentUser(authentication);
        appLockService.lock(user, session);
        return "redirect:/app-lock";
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
