package com.finance.controller;

import com.finance.model.entity.Account;
import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.security.UserPrincipal;
import com.finance.service.*;
import com.finance.service.excel.FinancialHealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
@AllArgsConstructor
public class HomeController {
    private static final List<String> DASHBOARD_CATEGORIES = List.of(
            "Food", "Shopping", "Transport", "Bills", "Entertainment",
            "Health", "Education", "Salary", "Investment", "Other"
    );

    private final UserRepository userRepository;
    private final UserService userService;
    private final AccountService accountService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final BudgetService budgetService;
    private final FinancialGoalService financialGoalService;
    private final ImportFileService importFileService;
    private final UserDataExportService userDataExportService;
    private final FinancialHealthScoreService financialHealthScoreService;

    @GetMapping
    public String home() {
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();
        List<Income> recentIncome = incomeService.getUserIncome(user);
        BigDecimal totalIncome = recentIncome.stream()
                .filter(income -> YearMonth.from(income.getIncomeDate()).equals(currentMonth))
                .map(Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("user", user);
         model.addAttribute("totalExpenses", expenseService.getTotalExpensesByDateRange(user, startOfMonth, endOfMonth));
         model.addAttribute("totalIncome", totalIncome);
         model.addAttribute("recentIncome", recentIncome);
        model.addAttribute("budgets", budgetService.getUserActiveBudgets(user));
        model.addAttribute("activeBudgets", budgetService.getUserActiveBudgets(user));
        model.addAttribute("goals", financialGoalService.getUserGoals(user));
        model.addAttribute("healthScore", financialHealthScoreService.calculateHealthScore(user));
        model.addAttribute("categorySummary", buildCategorySummary(user));

        return "dashboard";
    }

    private List<Map<String, Object>> buildCategorySummary(User user) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, String> dataNames = new HashMap<>();
        for (Expense expense : expenseService.getUserExpenses(user)) {
            mergeCategoryTotal(totals, dataNames, expense.getCategory(), expense.getAmount(), expense.getDescription());
        }
        for (Income income : incomeService.getUserIncome(user)) {
            mergeCategoryTotal(totals, dataNames, income.getCategory(), income.getAmount(), income.getDescription());
        }
        for (ImportedTransaction transaction : importFileService.getPendingImportedTransactions(user)) {
            if (transaction == null || transaction.getCategory() == null || transaction.getCategory().isBlank()) {
                continue;
            }

            String category = normalizeCategory(transaction.getCategory());
            if (category == null) {
                continue;
            }

            BigDecimal amount = transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount().abs();
            totals.merge(category, amount, (current, merged) -> current.add(merged));
            if (transaction.getDescription() != null && !transaction.getDescription().isBlank()) {
                dataNames.putIfAbsent(category, transaction.getDescription().trim());
            }
        }

        List<String> categories = new ArrayList<>(DASHBOARD_CATEGORIES);
        totals.keySet().stream()
            .filter(category -> !categories.contains(category))
            .sorted()
            .forEach(categories::add);

        List<Map<String, Object>> summary = new ArrayList<>();
        for (String category : categories) {
            BigDecimal amount = totals.getOrDefault(category, BigDecimal.ZERO);
            Map<String, Object> item = new HashMap<>();
            item.put("name", category);
            item.put("amount", amount);
            item.put("hasData", amount.compareTo(BigDecimal.ZERO) > 0);
                item.put("dataName", amount.compareTo(BigDecimal.ZERO) > 0
                    ? dataNames.getOrDefault(category, "Data available")
                    : null);
            summary.add(item);
        }
        return summary;
    }

    private void mergeCategoryTotal(Map<String, BigDecimal> totals, Map<String, String> dataNames,
                                    String category, BigDecimal amount, String dataName) {
        String normalizedCategory = normalizeCategory(category);
        if (normalizedCategory != null) {
            totals.merge(normalizedCategory, amount == null ? BigDecimal.ZERO : amount.abs(), (current, merged) -> current.add(merged));
            if (dataName != null && !dataName.isBlank()) {
                dataNames.putIfAbsent(normalizedCategory, dataName.trim());
            }
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String trimmed = category.trim();
        for (String validCategory : DASHBOARD_CATEGORIES) {
            if (validCategory.equalsIgnoreCase(trimmed)) {
                return validCategory;
            }
        }
        return trimmed;
    }

    @GetMapping("/account/export-data")
    @ResponseBody
    public ResponseEntity<byte[]> exportUserData(Authentication authentication,
                                                 @RequestParam(defaultValue = "csv") String format) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean excel = "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
        byte[] data = excel
                ? userDataExportService.exportExcel(user)
                : buildUserExportCsv(user).getBytes(StandardCharsets.UTF_8);
        String extension = excel ? "xlsx" : "csv";
        MediaType mediaType = excel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv; charset=UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"finwise-export-" + user.getId() + "." + extension + "\"")
                .contentType(mediaType)
                .body(data);
    }

    @PostMapping("/account/update-profile")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateProfile(Authentication authentication,
                                                            @RequestParam(value = "fullName", required = false) String fullName,
                                                            @RequestParam(value = "full_name", required = false) String fullNameUnderscore,
                                                            @RequestParam(value = "username", required = false) String username,
                                                            @RequestParam(value = "email", required = false) String email) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String previousUsername = user.getUsername();

        try {
            User updatedUser = userService.updateProfile(user,
                    fullName == null ? fullNameUnderscore : fullName,
                    username,
                    email);

            if (!updatedUser.getUsername().equals(previousUsername)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new UserPrincipal(updatedUser),
                                updatedUser.getPassword(),
                                authentication.getAuthorities()));
            }

            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully.", "username", updatedUser.getUsername()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "That username or email is already in use."));
        }
    }

    @PostMapping("/account/update-theme")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateTheme(Authentication authentication, @RequestBody Map<String, String> payload) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            User updatedUser = userService.updateTheme(user, payload == null ? null : payload.get("theme"));
            return ResponseEntity.ok(Map.of("success", true, "theme", updatedUser.getTheme()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/account/update-language")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateLanguage(Authentication authentication, @RequestBody Map<String, String> payload) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            User updatedUser = userService.updateLanguage(user, payload == null ? null : payload.get("language"));
            return ResponseEntity.ok(Map.of("success", true, "language", updatedUser.getLanguage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/account/update-notifications")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateNotifications(Authentication authentication, @RequestBody Map<String, Boolean> payload) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            User updatedUser = userService.updateNotificationPreferences(user, payload);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("notificationsEnabled", updatedUser.getNotificationsEnabled());
            response.put("budgetAlerts", updatedUser.getBudgetAlertsEnabled());
            response.put("dailySummary", updatedUser.getDailySummaryEnabled());
            response.put("goalUpdates", updatedUser.getGoalUpdatesEnabled());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/account/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(Authentication authentication,
                                                             @RequestBody Map<String, String> payload) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            userService.changePassword(user, payload == null ? null : payload.get("currentPassword"), payload == null ? null : payload.get("newPassword"));
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/account/delete-account")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAccount(Authentication authentication,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        userService.deleteAccountAndData(user);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.ok(Map.of("success", true, "message", "Account deleted successfully."));
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/chatbot")
    public String chatbot(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "chatbot";
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        model.addAttribute("user", user);
        model.addAttribute("totalIncome", incomeService.getTotalIncomeByDateRange(user, startOfMonth, endOfMonth));
        model.addAttribute("totalExpenses", expenseService.getTotalExpensesByDateRange(user, startOfMonth, endOfMonth));

        return "account";
    }

    private String buildUserExportCsv(User user) {
        String[] headers = {
                "record_type", "entity_id", "user_id", "username", "full_name", "email",
                "profile_image", "is_active", "created_at", "updated_at",
                "account_name", "account_type", "balance", "institution",
                "description", "amount", "transaction_date", "category", "income_source",
                "payment_method", "notes", "budget_month", "limit_amount", "spent_amount",
                "goal_name", "target_amount", "current_amount", "target_date", "goal_status", "priority",
                "original_filename", "file_type", "transaction_type", "import_status", "extracted_text", "balance_after"
        };

        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, headers);

        appendCsvRow(csv,
                "profile",
                String.valueOf(user.getId()),
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getProfileImage() == null ? "" : user.getProfileImage(),
                user.getIsActive() == null ? "" : String.valueOf(user.getIsActive()),
                formatDateTime(user.getCreatedAt()),
                formatDateTime(user.getUpdatedAt()),
                "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "", "",
                "", "", "", "", "", ""
        );

        List<Account> accounts = accountService.getUserAccounts(user);
        for (Account account : accounts) {
            appendCsvRow(csv,
                    "account",
                    String.valueOf(account.getId()),
                    String.valueOf(account.getUser().getId()),
                    account.getUser().getUsername(),
                    account.getUser().getFullName(),
                    account.getUser().getEmail(),
                    account.getUser().getProfileImage() == null ? "" : account.getUser().getProfileImage(),
                    account.getUser().getIsActive() == null ? "" : String.valueOf(account.getUser().getIsActive()),
                    formatDateTime(account.getUser().getCreatedAt()),
                    formatDateTime(account.getUser().getUpdatedAt()),
                    account.getAccountName(),
                    account.getAccountType(),
                    account.getBalance() == null ? "" : account.getBalance().toPlainString(),
                    account.getInstitution() == null ? "" : account.getInstitution(),
                    "", "", "", "", "",
                    "", "", "", "", "",
                    "", "", "", "", "", "",
                    "", "", "", "", "", ""
            );
        }

        List<Income> incomes = incomeService.getUserIncome(user);
        for (Income income : incomes) {
            appendCsvRow(csv,
                    "income",
                    String.valueOf(income.getId()),
                    String.valueOf(income.getUser().getId()),
                    income.getUser().getUsername(),
                    income.getUser().getFullName(),
                    income.getUser().getEmail(),
                    income.getUser().getProfileImage() == null ? "" : income.getUser().getProfileImage(),
                    income.getUser().getIsActive() == null ? "" : String.valueOf(income.getUser().getIsActive()),
                    formatDateTime(income.getUser().getCreatedAt()),
                    formatDateTime(income.getUser().getUpdatedAt()),
                    "", "", "",
                    "",
                    income.getDescription(),
                    income.getAmount() == null ? "" : income.getAmount().toPlainString(),
                    income.getIncomeDate() == null ? "" : income.getIncomeDate().toString(),
                    income.getCategory(),
                    income.getIncomeSource() == null ? "" : income.getIncomeSource(),
                    "",
                    income.getNotes() == null ? "" : income.getNotes(),
                    "", "", "",
                    "", "", "", "", "", "",
                    "", "", "", "", "", ""
            );
        }

        List<Expense> expenses = expenseService.getUserExpenses(user);
        for (Expense expense : expenses) {
            appendCsvRow(csv,
                    "expense",
                    String.valueOf(expense.getId()),
                    String.valueOf(expense.getUser().getId()),
                    expense.getUser().getUsername(),
                    expense.getUser().getFullName(),
                    expense.getUser().getEmail(),
                    expense.getUser().getProfileImage() == null ? "" : expense.getUser().getProfileImage(),
                    expense.getUser().getIsActive() == null ? "" : String.valueOf(expense.getUser().getIsActive()),
                    formatDateTime(expense.getUser().getCreatedAt()),
                    formatDateTime(expense.getUser().getUpdatedAt()),
                    "", "", "",
                    "",
                    expense.getDescription(),
                    expense.getAmount() == null ? "" : expense.getAmount().toPlainString(),
                    expense.getExpenseDate() == null ? "" : expense.getExpenseDate().toString(),
                    expense.getCategory(),
                    "",
                    expense.getPaymentMethod() == null ? "" : expense.getPaymentMethod(),
                    expense.getNotes() == null ? "" : expense.getNotes(),
                    "", "", "",
                    "", "", "", "", "", "",
                    "", "", "", "", "", ""
            );
        }

        List<Budget> budgets = budgetService.getUserAllBudgets(user);
        for (Budget budget : budgets) {
            appendCsvRow(csv,
                    "budget",
                    String.valueOf(budget.getId()),
                    String.valueOf(budget.getUser().getId()),
                    budget.getUser().getUsername(),
                    budget.getUser().getFullName(),
                    budget.getUser().getEmail(),
                    budget.getUser().getProfileImage() == null ? "" : budget.getUser().getProfileImage(),
                    budget.getUser().getIsActive() == null ? "" : String.valueOf(budget.getUser().getIsActive()),
                    formatDateTime(budget.getUser().getCreatedAt()),
                    formatDateTime(budget.getUser().getUpdatedAt()),
                    "", "", "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    budget.getCategory(),
                    "",
                    "",
                    "",
                    budget.getNotes() == null ? "" : budget.getNotes(),
                    budget.getBudgetMonth() == null ? "" : budget.getBudgetMonth(),
                    budget.getLimitAmount() == null ? "" : budget.getLimitAmount().toPlainString(),
                    budget.getSpentAmount() == null ? "" : budget.getSpentAmount().toPlainString(),
                    "", "", "", "", "", "",
                    "", "", "", "", "", ""
            );
        }

        List<FinancialGoal> goals = financialGoalService.getUserGoals(user);
        for (FinancialGoal goal : goals) {
            appendCsvRow(csv,
                    "goal",
                    String.valueOf(goal.getId()),
                    String.valueOf(goal.getUser().getId()),
                    goal.getUser().getUsername(),
                    goal.getUser().getFullName(),
                    goal.getUser().getEmail(),
                    goal.getUser().getProfileImage() == null ? "" : goal.getUser().getProfileImage(),
                    goal.getUser().getIsActive() == null ? "" : String.valueOf(goal.getUser().getIsActive()),
                    formatDateTime(goal.getUser().getCreatedAt()),
                    formatDateTime(goal.getUser().getUpdatedAt()),
                    "", "", "",
                    "",
                    goal.getGoalName(),
                    goal.getTargetAmount() == null ? "" : goal.getTargetAmount().toPlainString(),
                    goal.getTargetDate() == null ? "" : goal.getTargetDate().toString(),
                    "",
                    "",
                    goal.getDescription() == null ? "" : goal.getDescription(),
                    "",
                    "",
                    "",
                    goal.getTargetAmount() == null ? "" : goal.getTargetAmount().toPlainString(),
                    goal.getCurrentAmount() == null ? "" : goal.getCurrentAmount().toPlainString(),
                    goal.getTargetDate() == null ? "" : goal.getTargetDate().toString(),
                    goal.getGoalStatus() == null ? "" : goal.getGoalStatus(),
                    goal.getPriority() == null ? "" : goal.getPriority(),
                    "", "", "", "", "", ""
            );
        }

        List<ImportedTransaction> importedTransactions = importFileService.getImportedTransactions(user);
        for (ImportedTransaction transaction : importedTransactions) {
            appendCsvRow(csv,
                    "imported_transaction",
                    String.valueOf(transaction.getId()),
                    String.valueOf(transaction.getUser().getId()),
                    transaction.getUser().getUsername(),
                    transaction.getUser().getFullName(),
                    transaction.getUser().getEmail(),
                    transaction.getUser().getProfileImage() == null ? "" : transaction.getUser().getProfileImage(),
                    transaction.getUser().getIsActive() == null ? "" : String.valueOf(transaction.getUser().getIsActive()),
                    formatDateTime(transaction.getUser().getCreatedAt()),
                    formatDateTime(transaction.getUser().getUpdatedAt()),
                    "", "", "",
                    "",
                    transaction.getDescription(),
                    transaction.getAmount() == null ? "" : transaction.getAmount().toPlainString(),
                    transaction.getTransactionDate() == null ? "" : transaction.getTransactionDate().toString(),
                    transaction.getCategory() == null ? "" : transaction.getCategory(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "", "", "", "", "", "",
                    transaction.getOriginalFilename() == null ? "" : transaction.getOriginalFilename(),
                    transaction.getFileType() == null ? "" : transaction.getFileType(),
                    transaction.getTransactionType() == null ? "" : transaction.getTransactionType(),
                    transaction.getImportStatus() == null ? "" : transaction.getImportStatus(),
                    transaction.getExtractedText() == null ? "" : transaction.getExtractedText(),
                    transaction.getBalance() == null ? "" : transaction.getBalance().toPlainString()
            );
        }

        return csv.toString();
    }

    private void appendCsvRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values[i] == null ? "" : values[i]));
        }
        csv.append(System.lineSeparator());
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return '"' + escaped + '"';
        }
        return escaped;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.toString();
    }
}