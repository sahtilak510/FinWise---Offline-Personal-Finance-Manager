package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.excel.CategoryExcelService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
@AllArgsConstructor
public class CategoriesController {

    private final UserRepository userRepository;
    private final CategoryExcelService categoryExcelService;

    @GetMapping
    public String viewCategories(Authentication authentication, Model model,
                                 @RequestParam(value = "type", required = false) String type) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        var allCategories = categoryExcelService.getUserCategories(user, type);

        model.addAttribute("user", user);
        model.addAttribute("categories", allCategories);
        model.addAttribute("selectedType", type);
        model.addAttribute("incomeCategories", allCategories.stream()
                .filter(c -> "INCOME".equalsIgnoreCase(c.getType())).toList());
        model.addAttribute("expenseCategories", allCategories.stream()
                .filter(c -> "EXPENSE".equalsIgnoreCase(c.getType())).toList());

        return "categories";
    }

    @PostMapping("/add")
    public String addCategory(Authentication authentication,
                              @RequestParam String name,
                              @RequestParam String type,
                              @RequestParam(required = false) String icon,
                              @RequestParam(required = false) String color,
                              @RequestParam(required = false) String parentId,
                              RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            categoryExcelService.createCategory(user, name, type, parentId, icon, color);
            redirectAttributes.addFlashAttribute("successMessage", "Category added successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/categories";
    }

    @PostMapping("/update/{id}")
    public String updateCategory(Authentication authentication, @PathVariable String id,
                                 @RequestParam String name,
                                 @RequestParam String type,
                                 @RequestParam(required = false) String icon,
                                 @RequestParam(required = false) String color,
                                 RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            categoryExcelService.updateCategory(user, id, name, type, icon, color);
            redirectAttributes.addFlashAttribute("successMessage", "Category updated successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/delete/{id}")
    public String deleteCategory(Authentication authentication, @PathVariable String id,
                                 RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            categoryExcelService.deleteCategory(user, id);
            redirectAttributes.addFlashAttribute("successMessage", "Category deleted successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/categories";
    }
}
