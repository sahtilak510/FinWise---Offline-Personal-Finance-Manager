package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.AccountService;
import com.finance.service.excel.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AccountController(AccountService accountService, UserRepository userRepository,
                             NotificationService notificationService) {
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String listAccounts(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("accounts", accountService.getUserAccounts(user));
        model.addAttribute("totalBalance", accountService.getTotalBalance(user));
        return "accounts";
    }

    @GetMapping("/add")
    public String addForm(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "add-account";
    }

    @PostMapping("/add")
    public String createAccount(Authentication auth,
                                @RequestParam String accountName,
                                @RequestParam(required = false) String accountType,
                                @RequestParam(required = false) BigDecimal balance,
                                @RequestParam(required = false) String institution,
                                RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            accountService.createAccount(user, accountName, accountType, balance, institution);
            recordActivity(user, "Account created", accountName, redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Account created successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/accounts";
    }

    @PostMapping("/update/{id}")
    public String updateAccount(Authentication auth,
                                @PathVariable Long id,
                                @RequestParam String accountName,
                                @RequestParam String accountType,
                                @RequestParam BigDecimal balance,
                                @RequestParam(required = false) String institution,
                                RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            accountService.updateAccount(user, id, accountName, accountType, balance, institution);
            recordActivity(user, "Account updated", accountName, redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Account updated successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating account: " + e.getMessage());
        }
        return "redirect:/accounts";
    }

    @PostMapping("/delete/{id}")
    public String deleteAccount(Authentication auth, @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var account = accountService.getByIdAndUser(id, user);
        accountService.deleteAccount(id, user);
        account.ifPresent(deleted -> {
            recordActivity(user, "Account deleted", deleted.getAccountName(), redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Account deleted successfully!");
        });
        return "redirect:/accounts";
    }

    private void recordActivity(User user, String title, String description,
                                RedirectAttributes redirectAttributes) {
        if (!notificationService.recordActivity(user, title, description)) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "The action completed, but its notification could not be recorded.");
        }
    }
}
