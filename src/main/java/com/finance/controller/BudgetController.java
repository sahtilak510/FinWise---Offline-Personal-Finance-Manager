package com.finance.controller;

import com.finance.model.entity.Budget;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.BudgetService;
import com.finance.service.excel.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/budgets")
@AllArgsConstructor
public class BudgetController {
    private final BudgetService budgetService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @GetMapping
    public String viewBudgets(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("budgets", budgetService.getUserAllBudgets(user));
        return "budgets";
    }

    @GetMapping("/add")
    public String addBudgetForm(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "add-budget";
    }

    @PostMapping("/add")
    public String addBudget(
            @RequestParam String category,
            @RequestParam BigDecimal limitAmount,
            @RequestParam String budgetMonth,
            @RequestParam String notes,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            budgetService.createBudget(user, category, limitAmount, budgetMonth, notes);
            recordActivity(user, "Budget created", category, redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Budget created successfully!");
            return "redirect:/budgets";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating budget: " + e.getMessage());
            return "redirect:/budgets/add";
        }
    }

    @GetMapping("/edit/{id}")
    public String editBudgetForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        Budget budget = budgetService.getBudgetById(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        model.addAttribute("budget", budget);
        return "edit-budget";
    }

    @PostMapping("/update/{id}")
    public String updateBudget(
            @PathVariable Long id,
            @RequestParam BigDecimal limitAmount,
            @RequestParam BigDecimal spentAmount,
            @RequestParam String notes,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Budget budget = budgetService.getBudgetById(id, user)
                    .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
            budgetService.updateBudget(user, id, limitAmount, spentAmount, notes);
            recordActivity(user, "Budget updated", budget.getCategory(), redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Budget updated successfully!");
            return "redirect:/budgets";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating budget: " + e.getMessage());
            return "redirect:/budgets/edit/" + id;
        }
    }

    @PostMapping("/spent/{id}")
    public String updateBudgetSpent(
            @PathVariable Long id,
            @RequestParam BigDecimal spentAmount,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Budget budget = budgetService.getBudgetById(id, user)
                    .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
            budgetService.updateSpentAmount(user, id, spentAmount);
            recordActivity(user, "Budget spending updated", budget.getCategory(), redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Budget spending updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating spending: " + e.getMessage());
        }
        return "redirect:/budgets";
    }

    @PostMapping("/delete/{id}")
    public String deleteBudget(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Budget budget = budgetService.getBudgetById(id, user)
                    .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
            budgetService.deleteBudget(user, id);
            recordActivity(user, "Budget deleted", budget.getCategory(), redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Budget deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting budget: " + e.getMessage());
        }
        return "redirect:/budgets";
    }

    @PostMapping("/toggle/{id}")
    public String toggleBudgetActive(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Budget budget = budgetService.getBudgetById(id, user)
                    .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
            budgetService.toggleBudgetActive(user, id);
            recordActivity(user, "Budget status updated", budget.getCategory(), redirectAttributes);
            redirectAttributes.addFlashAttribute("successMessage", "Budget status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating budget: " + e.getMessage());
        }
        return "redirect:/budgets";
    }

    private void recordActivity(User user, String title, String description,
                                RedirectAttributes redirectAttributes) {
        if (!notificationService.recordActivity(user, title, description)) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "The action completed, but its notification could not be recorded.");
        }
    }
}
