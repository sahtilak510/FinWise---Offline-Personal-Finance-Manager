package com.finance.controller;

import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.FinancialGoalService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/goals")
@AllArgsConstructor
public class FinancialGoalController {
    private final FinancialGoalService financialGoalService;
    private final UserRepository userRepository;

    @GetMapping
    public String viewGoals(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("goals", financialGoalService.getUserGoals(user));
        return "goals";
    }

    @GetMapping("/add")
    public String addGoalForm(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "add-goal";
    }

    @PostMapping("/add")
    public String addGoal(
            @RequestParam String goalName,
            @RequestParam String description,
            @RequestParam BigDecimal targetAmount,
            @RequestParam String targetDate,
            @RequestParam String priority,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            financialGoalService.createGoal(user, goalName, description, targetAmount,
                    LocalDate.parse(targetDate), priority);
            
            redirectAttributes.addFlashAttribute("successMessage", "Financial goal created successfully!");
            return "redirect:/goals";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating goal: " + e.getMessage());
            return "redirect:/goals/add";
        }
    }

    @GetMapping("/edit/{id}")
    public String editGoalForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        FinancialGoal goal = financialGoalService.getGoalById(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        model.addAttribute("goal", goal);
        return "edit-goal";
    }

    @PostMapping("/update/{id}")
    public String updateGoal(
            @PathVariable Long id,
            @RequestParam String goalName,
            @RequestParam String description,
            @RequestParam BigDecimal targetAmount,
            @RequestParam String targetDate,
            @RequestParam String priority,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            financialGoalService.updateGoal(user, id, goalName, description, targetAmount,
                    LocalDate.parse(targetDate), priority);
            redirectAttributes.addFlashAttribute("successMessage", "Goal updated successfully!");
            return "redirect:/goals";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating goal: " + e.getMessage());
            return "redirect:/goals/edit/" + id;
        }
    }

    @PostMapping("/progress/{id}")
    public String updateGoalProgress(
            @PathVariable Long id,
            @RequestParam BigDecimal currentAmount,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            financialGoalService.updateGoalProgress(user, id, currentAmount);
            redirectAttributes.addFlashAttribute("successMessage", "Goal progress updated!");
            return "redirect:/goals";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating progress: " + e.getMessage());
            return "redirect:/goals";
        }
    }

    @PostMapping("/complete/{id}")
    public String completeGoal(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            financialGoalService.markGoalAsCompleted(user, id);
            redirectAttributes.addFlashAttribute("successMessage", "Goal marked as completed!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error completing goal: " + e.getMessage());
        }
        return "redirect:/goals";
    }

    @PostMapping("/delete/{id}")
    public String deleteGoal(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            financialGoalService.deleteGoal(user, id);
            redirectAttributes.addFlashAttribute("successMessage", "Goal deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting goal: " + e.getMessage());
        }
        return "redirect:/goals";
    }
}
