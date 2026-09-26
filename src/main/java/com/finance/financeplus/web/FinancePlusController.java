package com.finance.financeplus.web;

import com.finance.financeplus.model.FinancePlusAttachment;
import com.finance.financeplus.model.FinancePlusBackup;
import com.finance.financeplus.model.FinancePlusNotification;
import com.finance.financeplus.model.FinancePlusRecurring;
import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.financeplus.service.FinancePlusAttachmentService;
import com.finance.financeplus.service.FinancePlusBackupService;
import com.finance.financeplus.service.FinancePlusExportService;
import com.finance.financeplus.service.FinancePlusImportService;
import com.finance.financeplus.service.FinancePlusNotificationService;
import com.finance.financeplus.service.FinancePlusRecurringService;
import com.finance.financeplus.service.FinancePlusSettingsService;
import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import com.finance.repository.UserRepository;
import com.finance.service.CashFlowForecastService;
import com.finance.service.ImportFileService;
import com.finance.service.UserDataExportService;
import com.finance.service.excel.FinancialHealthScoreService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/finance-plus")
public class FinancePlusController {

    private static final String UNLOCKED_SESSION_PREFIX = "finance_plus_unlocked_";

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final ImportedTransactionRepository importedTransactionRepository;
    private final FinancePlusSettingsService settingsService;
    private final FinancePlusRecurringService recurringService;
    private final FinancePlusNotificationService notificationService;
    private final FinancePlusAttachmentService attachmentService;
    private final FinancePlusImportService importService;
    private final FinancePlusExportService exportService;
    private final FinancePlusBackupService backupService;
    private final UserDataExportService userDataExportService;
    private final CashFlowForecastService forecastService;
    private final FinancialHealthScoreService healthScoreService;

    public FinancePlusController(UserRepository userRepository,
                                  ExpenseRepository expenseRepository,
                                  IncomeRepository incomeRepository,
                                  ImportedTransactionRepository importedTransactionRepository,
                                  FinancePlusSettingsService settingsService,
                                  FinancePlusRecurringService recurringService,
                                  FinancePlusNotificationService notificationService,
                                  FinancePlusAttachmentService attachmentService,
                                  FinancePlusImportService importService,
                                  FinancePlusExportService exportService,
                                  FinancePlusBackupService backupService,
                                  UserDataExportService userDataExportService,
                                  CashFlowForecastService forecastService,
                                  FinancialHealthScoreService healthScoreService) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.importedTransactionRepository = importedTransactionRepository;
        this.settingsService = settingsService;
        this.recurringService = recurringService;
        this.notificationService = notificationService;
        this.attachmentService = attachmentService;
        this.importService = importService;
        this.exportService = exportService;
        this.backupService = backupService;
        this.userDataExportService = userDataExportService;
        this.forecastService = forecastService;
        this.healthScoreService = healthScoreService;
    }

    @GetMapping
    public String financePlus(Authentication authentication, HttpSession session, Model model,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate,
                              @RequestParam(required = false) BigDecimal minAmount,
                              @RequestParam(required = false) BigDecimal maxAmount) {
        User user = currentUser(authentication);
        if (isLocked(user, session)) {
            return "redirect:/finance-plus/locked";
        }
        notificationService.generateForUser(user);
        model.addAttribute("user", user);
        model.addAttribute("settings", settingsService.getSettings(user));
        model.addAttribute("forecast", forecastService.forecast(user, YearMonth.now().plusMonths(1)));
        model.addAttribute("upcomingBills", upcomingBills(user));
        model.addAttribute("healthScore", healthScoreService.calculateHealthScore(user));
        model.addAttribute("recurringRules", recurringService.getRules(user));
        model.addAttribute("notifications", notificationService.getNotifications(user));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user));
        model.addAttribute("attachments", attachmentService.getAttachments(user));
        model.addAttribute("backups", backupService.getBackups(user));
        model.addAttribute("vaultUnlocked", backupService.isVaultUnlocked(user));
        model.addAttribute("searchResults", search(user, q, type, category, startDate, endDate, minAmount, maxAmount));
        model.addAttribute("q", q);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);
        model.addAttribute("expenseCategories", expenseRepository.findByUserOrderByExpenseDateDesc(user).stream()
                .map(Expense::getCategory).filter(value -> value != null && !value.isBlank()).distinct().sorted().toList());
        model.addAttribute("incomeCategories", incomeRepository.findByUserOrderByIncomeDateDesc(user).stream()
                .map(Income::getCategory).filter(value -> value != null && !value.isBlank()).distinct().sorted().toList());
        return "finance-plus";
    }

    @GetMapping("/locked")
    public String locked(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        return "finance-plus-locked";
    }

    @PostMapping("/unlock")
    public String unlock(Authentication authentication, HttpSession session,
                         @RequestParam String passcode, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (settingsService.verifyPasscode(user, passcode)) {
            session.setAttribute(sessionKey(user), settingsService.passcodeVersion(user) + ":" + settingsService.unlockExpiresAt());
            return "redirect:/finance-plus";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "Incorrect passcode.");
        return "redirect:/finance-plus/locked";
    }

    @PostMapping("/lock")
    public String lock(Authentication authentication, HttpSession session) {
        User user = currentUser(authentication);
        session.removeAttribute(sessionKey(user));
        backupService.lockVault(user);
        return "redirect:/finance-plus/locked";
    }

    @PostMapping("/preferences")
    public String updatePreferences(Authentication authentication,
                                    @RequestParam(defaultValue = "false") boolean billRemindersEnabled,
                                    @RequestParam int billReminderDays,
                                    @RequestParam(defaultValue = "false") boolean largeExpenseAlertsEnabled,
                                    @RequestParam BigDecimal largeExpenseThreshold,
                                    @RequestParam(defaultValue = "false") boolean weeklySummaryEnabled,
                                    @RequestParam(defaultValue = "false") boolean monthlySummaryEnabled,
                                    RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        settingsService.updatePreferences(user, billRemindersEnabled, billReminderDays,
                largeExpenseAlertsEnabled, largeExpenseThreshold, weeklySummaryEnabled, monthlySummaryEnabled);
        redirectAttributes.addFlashAttribute("successMessage", "Finance Plus preferences saved.");
        return "redirect:/finance-plus#preferences";
    }

    @PostMapping("/backup-preferences")
    public String updateBackupPreferences(Authentication authentication,
                                          @RequestParam(defaultValue = "false") boolean autoBackupEnabled,
                                          @RequestParam int automaticBackupHour,
                                          RedirectAttributes redirectAttributes) {
        settingsService.updateBackupPreferences(currentUser(authentication), autoBackupEnabled, automaticBackupHour);
        redirectAttributes.addFlashAttribute("successMessage", "Automatic backup preference saved.");
        return "redirect:/finance-plus#backups";
    }

    @PostMapping("/passcode")
    public String setPasscode(Authentication authentication,
                              @RequestParam(required = false, defaultValue = "") String currentPasscode,
                              @RequestParam String passcode,
                              @RequestParam String confirmPasscode,
                              RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (!passcode.equals(confirmPasscode)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Passcodes do not match.");
        } else {
            try {
                settingsService.setPasscode(user, currentPasscode, passcode);
                redirectAttributes.addFlashAttribute("successMessage", "Finance Plus passcode enabled.");
            } catch (RuntimeException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            }
        }
        return "redirect:/finance-plus#settings";
    }

    @PostMapping("/passcode/disable")
    public String disablePasscode(Authentication authentication,
                                  @RequestParam(required = false, defaultValue = "") String currentPasscode,
                                  RedirectAttributes redirectAttributes) {
        try {
            settingsService.disablePasscode(currentUser(authentication), currentPasscode);
            redirectAttributes.addFlashAttribute("successMessage", "Finance Plus passcode disabled.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/finance-plus#settings";
    }

    @PostMapping("/recurring")
    public String saveRecurring(Authentication authentication,
                                @RequestParam(required = false) Long id,
                                @RequestParam String transactionType,
                                @RequestParam String description,
                                @RequestParam BigDecimal amount,
                                @RequestParam String category,
                                @RequestParam(defaultValue = "Other") String paymentMethod,
                                @RequestParam String frequency,
                                @RequestParam LocalDate nextDueDate,
                                @RequestParam(defaultValue = "true") boolean active,
                                @RequestParam(defaultValue = "false") boolean autoPost,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            if (id == null) {
                recurringService.create(user, transactionType, description, amount, category, paymentMethod,
                        frequency, nextDueDate, autoPost);
            } else {
                recurringService.update(user, id, transactionType, description, amount, category, paymentMethod,
                        frequency, nextDueDate, active, autoPost);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Recurring rule saved.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/finance-plus#recurring";
    }

    @PostMapping("/recurring/delete/{id}")
    public String deleteRecurring(Authentication authentication, @PathVariable Long id) {
        recurringService.delete(currentUser(authentication), id);
        return "redirect:/finance-plus#recurring";
    }

    @PostMapping("/notifications/read/{id}")
    public String readNotification(Authentication authentication, @PathVariable Long id) {
        notificationService.markRead(currentUser(authentication), id);
        return "redirect:/finance-plus#notifications";
    }

    @PostMapping("/notifications/read-all")
    public String readAllNotifications(Authentication authentication) {
        notificationService.markAllRead(currentUser(authentication));
        return "redirect:/finance-plus#notifications";
    }

    @PostMapping("/attachments")
    public String uploadAttachment(Authentication authentication,
                                   @RequestParam String transactionType,
                                   @RequestParam Long transactionId,
                                   @RequestParam MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        try {
            attachmentService.upload(currentUser(authentication), transactionType, transactionId, file);
            redirectAttributes.addFlashAttribute("successMessage", "Attachment uploaded.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/finance-plus#attachments";
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> downloadAttachment(Authentication authentication, @PathVariable Long id) {
        FinancePlusAttachment attachment = attachmentService.get(currentUser(authentication), id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(attachment.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(attachment.getData());
    }

    @PostMapping("/attachments/delete/{id}")
    public String deleteAttachment(Authentication authentication, @PathVariable Long id) {
        attachmentService.delete(currentUser(authentication), id);
        return "redirect:/finance-plus#attachments";
    }

    @PostMapping("/import")
    public String importFile(Authentication authentication, @RequestParam MultipartFile file,
                             RedirectAttributes redirectAttributes) {
        try {
            FinancePlusImportService.ValidationResult result = importService.importAndValidate(file, currentUser(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Import validated: " + result.valid()
                    + " accepted, " + result.duplicates() + " duplicates, " + result.rejected() + " rejected.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + exception.getMessage());
        }
        return "redirect:/finance-plus#import-export";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication,
                                         @RequestParam(defaultValue = "csv") String format) {
        User user = currentUser(authentication);
        byte[] data;
        String extension;
        String contentType;
        if ("xlsx".equalsIgnoreCase(format)) {
            data = userDataExportService.exportExcel(user);
            extension = "xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else {
            data = exportService.exportCsv(user);
            extension = "csv";
            contentType = "text/csv;charset=UTF-8";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("finwise-user-" + user.getId() + "." + extension, StandardCharsets.UTF_8).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @PostMapping("/vault/unlock")
    public String unlockVault(Authentication authentication, @RequestParam String passphrase,
                              RedirectAttributes redirectAttributes) {
        try {
            backupService.unlockVault(currentUser(authentication), passphrase);
            redirectAttributes.addFlashAttribute("successMessage", "Backup vault unlocked for 30 minutes.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/finance-plus#backups";
    }

    @PostMapping("/vault/lock")
    public String lockVault(Authentication authentication) {
        backupService.lockVault(currentUser(authentication));
        return "redirect:/finance-plus#backups";
    }

    @PostMapping("/backups")
    public String createBackup(Authentication authentication, @RequestParam String passphrase,
                               RedirectAttributes redirectAttributes) {
        try {
            backupService.createBackup(currentUser(authentication), passphrase, "MANUAL");
            redirectAttributes.addFlashAttribute("successMessage", "Encrypted backup created.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/finance-plus#backups";
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<byte[]> downloadBackup(Authentication authentication, @PathVariable Long id) {
        FinancePlusBackup backup = backupService.getBackupForUser(currentUser(authentication), id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(backup.getFileName()).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(backupService.download(currentUser(authentication), id));
    }

    @PostMapping("/backups/{id}/restore")
    public String restoreBackup(Authentication authentication, @PathVariable Long id,
                                @RequestParam String passphrase, RedirectAttributes redirectAttributes) {
        try {
            backupService.restore(currentUser(authentication), id, passphrase);
            redirectAttributes.addFlashAttribute("successMessage", "Backup restored. A safety backup was created first.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/finance-plus#backups";
    }

    @PostMapping("/backups/{id}/delete")
    public String deleteBackup(Authentication authentication, @PathVariable Long id) {
        backupService.delete(currentUser(authentication), id);
        return "redirect:/finance-plus#backups";
    }

    private List<CashFlowForecastService.UpcomingBill> upcomingBills(User user) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(30);
        List<CashFlowForecastService.UpcomingBill> bills = new ArrayList<>(forecastService.upcomingBills(user, 30));
        recurringService.getActiveRules(user).stream()
                .filter(rule -> "EXPENSE".equals(rule.getTransactionType()))
                .filter(rule -> !rule.getNextDueDate().isBefore(today) && !rule.getNextDueDate().isAfter(limit))
                .map(rule -> new CashFlowForecastService.UpcomingBill(
                        rule.getDescription(), rule.getAmount(), rule.getNextDueDate(), rule.getFrequency()))
                .forEach(bills::add);
        return bills.stream()
                .distinct()
                .sorted(Comparator.comparing(CashFlowForecastService.UpcomingBill::dueDate)
                        .thenComparing(CashFlowForecastService.UpcomingBill::description))
                .toList();
    }

    private List<TransactionResult> search(User user, String query, String type, String category,
                                           String startDateValue, String endDateValue,
                                           BigDecimal minAmount, BigDecimal maxAmount) {
        String queryText = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String typeFilter = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        LocalDate startDate = parseDate(startDateValue);
        LocalDate endDate = parseDate(endDateValue);
        List<TransactionResult> results = new ArrayList<>();
        if (typeFilter.isBlank() || typeFilter.equals("EXPENSE")) {
            for (Expense expense : expenseRepository.findByUserOrderByExpenseDateDesc(user)) {
                if (matches(expense.getDescription(), expense.getCategory(), expense.getNotes(), queryText)
                        && matchesCategory(category, expense.getCategory())
                        && matchesDate(expense.getExpenseDate(), startDate, endDate)
                        && matchesAmount(expense.getAmount(), minAmount, maxAmount)) {
                    results.add(new TransactionResult("EXPENSE", expense.getId(), expense.getExpenseDate(),
                            expense.getDescription(), expense.getCategory(), expense.getAmount(), "RECORDED"));
                }
            }
        }
        if (typeFilter.isBlank() || typeFilter.equals("INCOME")) {
            for (Income income : incomeRepository.findByUserOrderByIncomeDateDesc(user)) {
                if (matches(income.getDescription(), income.getCategory(), income.getNotes(), queryText)
                        && matchesCategory(category, income.getCategory())
                        && matchesDate(income.getIncomeDate(), startDate, endDate)
                        && matchesAmount(income.getAmount(), minAmount, maxAmount)) {
                    results.add(new TransactionResult("INCOME", income.getId(), income.getIncomeDate(),
                            income.getDescription(), income.getCategory(), income.getAmount(), "RECORDED"));
                }
            }
        }
        if (typeFilter.isBlank() || typeFilter.equals("IMPORT")) {
            for (ImportedTransaction imported : importedTransactionRepository.findByUserOrderByTransactionDateDesc(user)) {
                if (ImportFileService.isCommitted(imported)) {
                    continue;
                }
                if (matches(imported.getDescription(), imported.getCategory(), imported.getExtractedText(), queryText)
                        && matchesCategory(category, imported.getCategory())
                        && matchesDate(imported.getTransactionDate(), startDate, endDate)
                        && matchesAmount(imported.getAmount(), minAmount, maxAmount)) {
                    results.add(new TransactionResult(imported.getTransactionType(), imported.getId(),
                            imported.getTransactionDate(), imported.getDescription(), imported.getCategory(),
                            imported.getAmount(), imported.getImportStatus()));
                }
            }
        }
        results.sort(Comparator.comparing(TransactionResult::date).reversed());
        return results;
    }

    private boolean matches(String description, String category, String notes, String query) {
        return query.isBlank() || contains(description, query) || contains(category, query) || contains(notes, query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesCategory(String selected, String value) {
        return selected == null || selected.isBlank() || selected.equalsIgnoreCase(value);
    }

    private boolean matchesDate(LocalDate value, LocalDate start, LocalDate end) {
        return value != null && (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    private boolean matchesAmount(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return false;
        }
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isLocked(User user, HttpSession session) {
        if (!settingsService.isPasscodeEnabled(user)) {
            return false;
        }
        Object value = session == null ? null : session.getAttribute(sessionKey(user));
        if (!(value instanceof String state)) {
            return true;
        }
        int separator = state.indexOf(':');
        if (separator <= 0) {
            return true;
        }
        try {
            return !state.substring(0, separator).equals(settingsService.passcodeVersion(user))
                    || Long.parseLong(state.substring(separator + 1)) <= System.currentTimeMillis();
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private String sessionKey(User user) {
        return UNLOCKED_SESSION_PREFIX + user.getId();
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public record TransactionResult(String type, Long id, LocalDate date, String description,
                                     String category, BigDecimal amount, String status) {}
}
