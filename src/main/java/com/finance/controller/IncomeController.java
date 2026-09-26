package com.finance.controller;

import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.IncomeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/income")
public class IncomeController {
    private final IncomeService incomeService;
    private final UserRepository userRepository;

    public IncomeController(IncomeService incomeService, UserRepository userRepository) {
        this.incomeService = incomeService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String viewIncome(Authentication authentication, Model model,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) BigDecimal minAmount,
                             @RequestParam(required = false) BigDecimal maxAmount,
                             @RequestParam(required = false) Boolean recurring) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("incomes", incomeService.searchIncome(user, q, category,
                parseDate(startDate), parseDate(endDate), minAmount, maxAmount, recurring));
        model.addAttribute("categories", incomeService.getUserCategories(user));
        model.addAttribute("q", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);
        model.addAttribute("recurringFilter", recurring);
        return "income";
    }

    @GetMapping("/add")
    public String addIncomeForm(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "add-income";
    }

    @PostMapping("/add")
    public String addIncome(
            @RequestParam String description,
            @RequestParam BigDecimal amount,
            @RequestParam String incomeDate,
            @RequestParam String category,
            @RequestParam String incomeSource,
            @RequestParam String notes,
            @RequestParam(defaultValue = "false") boolean recurring,
            @RequestParam(required = false) String recurrenceFrequency,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            incomeService.addIncome(user, description, amount, LocalDate.parse(incomeDate),
                    category, incomeSource, notes, recurring, recurrenceFrequency);
            
            redirectAttributes.addFlashAttribute("successMessage", "Income added successfully!");
            return "redirect:/income";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding income: " + e.getMessage());
            return "redirect:/income/add";
        }
    }

    @GetMapping("/edit/{id}")
    public String editIncomeForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        Income income = incomeService.getIncomeById(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Income not found"));
        model.addAttribute("income", income);
        return "edit-income";
    }

    @PostMapping("/update/{id}")
    public String updateIncome(
            @PathVariable Long id,
            @RequestParam String description,
            @RequestParam BigDecimal amount,
            @RequestParam String incomeDate,
            @RequestParam String category,
            @RequestParam String incomeSource,
            @RequestParam String notes,
            @RequestParam(defaultValue = "false") boolean recurring,
            @RequestParam(required = false) String recurrenceFrequency,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            incomeService.updateIncome(user, id, description, amount, LocalDate.parse(incomeDate),
                    category, incomeSource, notes, recurring, recurrenceFrequency);
            redirectAttributes.addFlashAttribute("successMessage", "Income updated successfully!");
            return "redirect:/income";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating income: " + e.getMessage());
            return "redirect:/income/edit/" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteIncome(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            incomeService.deleteIncome(user, id);
            redirectAttributes.addFlashAttribute("successMessage", "Income deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting income: " + e.getMessage());
        }
        return "redirect:/income";
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }
}
