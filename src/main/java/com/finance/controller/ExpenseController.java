package com.finance.controller;

import com.finance.model.entity.Expense;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.ExpenseService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/expenses")
public class ExpenseController {
    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    public ExpenseController(ExpenseService expenseService, UserRepository userRepository) {
        this.expenseService = expenseService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String viewExpenses(Authentication authentication, Model model,
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
        model.addAttribute("expenses", expenseService.searchExpenses(user, q, category,
                parseDate(startDate), parseDate(endDate), minAmount, maxAmount, recurring));
        model.addAttribute("categories", expenseService.getUserCategories(user));
        model.addAttribute("q", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);
        model.addAttribute("recurringFilter", recurring);
        return "expenses";
    }

    @GetMapping("/add")
    public String addExpenseForm(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "add-expense";
    }

    @PostMapping("/add")
    public String addExpense(
            @RequestParam String description,
            @RequestParam BigDecimal amount,
            @RequestParam String expenseDate,
            @RequestParam String category,
            @RequestParam String paymentMethod,
            @RequestParam String notes,
            @RequestParam(defaultValue = "false") boolean recurring,
            @RequestParam(required = false) String recurrenceFrequency,
            @RequestParam(required = false) MultipartFile receipt,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            expenseService.addExpense(user, description, amount, LocalDate.parse(expenseDate),
                    category, paymentMethod, notes, readReceipt(receipt), recurring, recurrenceFrequency);
            
            redirectAttributes.addFlashAttribute("successMessage", "Expense added successfully!");
            return "redirect:/expenses";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding expense: " + e.getMessage());
            return "redirect:/expenses/add";
        }
    }

    @GetMapping("/edit/{id}")
    public String editExpenseForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        Expense expense = expenseService.getExpenseById(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        model.addAttribute("expense", expense);
        return "edit-expense";
    }

    @PostMapping("/update/{id}")
    public String updateExpense(
            @PathVariable Long id,
            @RequestParam String description,
            @RequestParam BigDecimal amount,
            @RequestParam String expenseDate,
            @RequestParam String category,
            @RequestParam String paymentMethod,
            @RequestParam String notes,
            @RequestParam(defaultValue = "false") boolean recurring,
            @RequestParam(required = false) String recurrenceFrequency,
            @RequestParam(required = false) MultipartFile receipt,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Expense existing = expenseService.getExpenseById(id, user)
                    .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
            byte[] receiptImage = readReceipt(receipt);
            if (receiptImage == null) {
                receiptImage = existing.getReceiptImage();
            }
            expenseService.updateExpense(user, id, description, amount, LocalDate.parse(expenseDate),
                    category, paymentMethod, notes, recurring, recurrenceFrequency);
            if (receiptImage != null) {
                existing.setReceiptImage(receiptImage);
                expenseService.saveExpense(existing);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Expense updated successfully!");
            return "redirect:/expenses";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating expense: " + e.getMessage());
            return "redirect:/expenses/edit/" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteExpense(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            expenseService.deleteExpense(user, id);
            redirectAttributes.addFlashAttribute("successMessage", "Expense deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting expense: " + e.getMessage());
        }
        return "redirect:/expenses";
    }

    @GetMapping("/receipt/{id}")
    public ResponseEntity<byte[]> getReceipt(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Expense expense = expenseService.getExpenseById(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        if (expense.getReceiptImage() == null || expense.getReceiptImage().length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(expense.getReceiptImage().length);
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(expense.getReceiptImage());
    }

    private byte[] readReceipt(MultipartFile receipt) throws java.io.IOException {
        if (receipt == null || receipt.isEmpty()) {
            return null;
        }
        String contentType = receipt.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Receipt must be an image file");
        }
        if (receipt.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Receipt image must be 5 MB or smaller");
        }
        return receipt.getBytes();
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
