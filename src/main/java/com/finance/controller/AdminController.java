package com.finance.controller;

import com.finance.financeplus.repository.FinancePlusAttachmentRepository;
import com.finance.financeplus.repository.FinancePlusBackupRepository;
import com.finance.financeplus.repository.FinancePlusNotificationRepository;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.financeplus.repository.FinancePlusSettingsRepository;
import com.finance.model.entity.User;
import com.finance.repository.AccountRepository;
import com.finance.repository.BudgetRepository;
import com.finance.repository.ChatMessageRepository;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.FinancialGoalRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import com.finance.repository.UserRepository;
import com.finance.service.AdminUserDeletionService;
import com.finance.service.AdminPasswordResetService;
import com.finance.service.AdminUserExportService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
@AllArgsConstructor
public class AdminController {
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final FinancialGoalRepository financialGoalRepository;
    private final ImportedTransactionRepository importedTransactionRepository;
    private final IncomeRepository incomeRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FinancePlusAttachmentRepository attachmentRepository;
    private final FinancePlusBackupRepository backupRepository;
    private final FinancePlusNotificationRepository notificationRepository;
    private final FinancePlusRecurringRepository recurringRepository;
    private final FinancePlusSettingsRepository settingsRepository;
    private final AdminUserDeletionService adminUserDeletionService;
    private final AdminPasswordResetService adminPasswordResetService;
    private final AdminUserExportService adminUserExportService;

    @GetMapping("/admin")
    public String dashboard(Model model) {
        List<Map<String, Object>> tables = List.of(
                table("Users", userRepository.count()),
                table("Accounts", accountRepository.count()),
                table("Budgets", budgetRepository.count()),
                table("Expenses", expenseRepository.count()),
                table("Financial goals", financialGoalRepository.count()),
                table("Imported transactions", importedTransactionRepository.count()),
                table("Income", incomeRepository.count()),
                table("Chat history", chatMessageRepository.count()),
                table("Finance Plus attachments", attachmentRepository.count()),
                table("Finance Plus backups", backupRepository.count()),
                table("Finance Plus notifications", notificationRepository.count()),
                table("Finance Plus recurring items", recurringRepository.count()),
                table("Finance Plus settings", settingsRepository.count())
        );

        model.addAttribute("users", userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("tables", tables);
        model.addAttribute("recordCount", tables.stream()
                .mapToLong(table -> (Long) table.get("records"))
                .sum());
        return "admin/dashboard";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "That user no longer exists.");
        } else if (authentication.getName().equals(user.getUsername())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot delete the account used for this admin session.");
        } else {
            adminUserDeletionService.deleteUserAndData(user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "User " + user.getUsername() + " and their associated data were deleted.");
        }
        return "redirect:/admin";
    }

    @GetMapping("/admin/users/{id}/export.xlsx")
    @ResponseBody
    public ResponseEntity<byte[]> exportUserExcel(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"finwise-user-" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(adminUserExportService.exportExcel(user));
    }

    @GetMapping("/admin/users/{id}/export.zip")
    @ResponseBody
    public ResponseEntity<byte[]> exportUserZip(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"finwise-user-" + id + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(adminUserExportService.exportZip(user));
    }

    @PostMapping("/admin/users/{id}/password-reset")
    public String createPasswordReset(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "That user no longer exists.");
            return "redirect:/admin";
        }

        String token = adminPasswordResetService.createResetToken(user);
        String resetUrl = "/reset-password?userId=" + id + "&token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        redirectAttributes.addFlashAttribute("resetUsername", user.getUsername());
        redirectAttributes.addFlashAttribute("resetUrl", resetUrl);
        return "redirect:/admin";
    }

    private Map<String, Object> table(String name, long records) {
        return Map.of("name", name, "records", records);
    }
}
