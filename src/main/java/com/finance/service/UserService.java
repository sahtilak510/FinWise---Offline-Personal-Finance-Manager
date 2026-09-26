package com.finance.service;

import com.finance.model.entity.*;
import com.finance.repository.*;
import com.finance.service.excel.LocalExcelStorageService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class UserService {
    private static final List<String> USER_DATA_SHEETS = List.of(
            "Users", "Profiles", "Income", "Expenses", "Budgets", "Goals",
            "Categories", "Settings", "Imports", "OCR_Data", "Duplicate_Checks",
            "Analytics", "Forecasts", "Reports", "AI_Chat", "Audit", "Notifications"
    );

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetRepository budgetRepository;
    private final FinancialGoalRepository financialGoalRepository;
    private final ImportedTransactionRepository importedTransactionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PasswordEncoder passwordEncoder;
    private final LocalExcelStorageService localExcelStorageService;

    public UserService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       ExpenseRepository expenseRepository,
                       IncomeRepository incomeRepository,
                       BudgetRepository budgetRepository,
                       FinancialGoalRepository financialGoalRepository,
                       ImportedTransactionRepository importedTransactionRepository,
                       ChatMessageRepository chatMessageRepository,
                       PasswordEncoder passwordEncoder,
                       LocalExcelStorageService localExcelStorageService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.budgetRepository = budgetRepository;
        this.financialGoalRepository = financialGoalRepository;
        this.importedTransactionRepository = importedTransactionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.passwordEncoder = passwordEncoder;
        this.localExcelStorageService = localExcelStorageService;
    }

    public User registerUser(String username, String email, String password, String fullName) {
        String trimmedUsername = username == null ? "" : username.trim();
        String trimmedEmail = email == null ? "" : email.trim();
        String trimmedFullName = fullName == null ? "" : fullName.trim();
        String trimmedPassword = password == null ? "" : password;

        if (trimmedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (trimmedEmail.isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (trimmedPassword.isEmpty() || trimmedPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (trimmedFullName.isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setUsername(trimmedUsername);
        user.setEmail(trimmedEmail);
        user.setPassword(passwordEncoder.encode(trimmedPassword));
        user.setFullName(trimmedFullName);
        user.setIsActive(true);
        user.setTheme("light");
        user.setLanguage("en");
        user.setNotificationsEnabled(true);
        user.setBudgetAlertsEnabled(true);
        user.setDailySummaryEnabled(true);
        user.setGoalUpdatesEnabled(true);

        return userRepository.save(user);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserById(Long id) {
        return id != null ? userRepository.findById(id) : Optional.empty();
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, String email, String fullName) {
        if (id == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setEmail(email);
        user.setFullName(fullName);
        return userRepository.save(user);
    }

    public User updateProfile(User user, String fullName, String username, String email) {
        String normalizedFullName = fullName == null ? user.getFullName() : fullName.trim();
        String normalizedUsername = username == null ? user.getUsername() : username.trim();
        String normalizedEmail = email == null ? user.getEmail() : email.trim();

        if (normalizedFullName == null || normalizedFullName.isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (normalizedUsername == null || normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (normalizedEmail == null || normalizedEmail.isEmpty() || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("A valid email is required");
        }

        Optional<User> existingByUsername = userRepository.findByUsername(normalizedUsername);
        if (existingByUsername.isPresent() && !existingByUsername.get().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Username already exists");
        }

        Optional<User> existingByEmail = userRepository.findByEmail(normalizedEmail);
        if (existingByEmail.isPresent() && !existingByEmail.get().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Email already exists");
        }

        user.setFullName(normalizedFullName);
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        return userRepository.save(user);
    }

    public User updateTheme(User user, String theme) {
        String normalizedTheme = theme == null ? null : theme.trim().toLowerCase();
        // Light or Dark only. Legacy 'auto'/blank values migrate to 'light'
        // so older accounts keep working without a second theme system.
        if (normalizedTheme == null || normalizedTheme.isBlank() || "auto".equals(normalizedTheme)) {
            String current = user.getTheme() == null ? "" : user.getTheme().trim().toLowerCase();
            if ("dark".equals(current) || "light".equals(current)) {
                normalizedTheme = current;
            } else {
                normalizedTheme = "light";
            }
        }
        if (!"dark".equals(normalizedTheme) && !"light".equals(normalizedTheme)) {
            throw new IllegalArgumentException("Theme must be dark or light");
        }
        user.setTheme(normalizedTheme);
        User savedUser = userRepository.save(user);
        persistPreference(savedUser, "theme", normalizedTheme);
        return savedUser;
    }

    public User updateLanguage(User user, String language) {
        // ONE global language system: English (default), Hindi, Tamil, Nepali.
        // Anything else (including legacy values) falls back to English —
        // never persist an unsupported language.
        String normalizedLanguage = language == null ? "en" : language.trim().toLowerCase();
        if (!"en".equals(normalizedLanguage) && !"hi".equals(normalizedLanguage)
                && !"ta".equals(normalizedLanguage) && !"ne".equals(normalizedLanguage)) {
            normalizedLanguage = "en";
        }
        user.setLanguage(normalizedLanguage);
        User savedUser = userRepository.save(user);
        persistPreference(savedUser, "language", normalizedLanguage);
        return savedUser;
    }

    public User updateNotificationPreferences(User user, Map<String, Boolean> preferences) {
        if (preferences == null) {
            return user;
        }
        user.setNotificationsEnabled(Boolean.TRUE.equals(preferences.getOrDefault("notificationsEnabled", user.getNotificationsEnabled())));
        user.setBudgetAlertsEnabled(Boolean.TRUE.equals(preferences.getOrDefault("budgetAlerts", user.getBudgetAlertsEnabled())));
        user.setDailySummaryEnabled(Boolean.TRUE.equals(preferences.getOrDefault("dailySummary", user.getDailySummaryEnabled())));
        user.setGoalUpdatesEnabled(Boolean.TRUE.equals(preferences.getOrDefault("goalUpdates", user.getGoalUpdatesEnabled())));
        User savedUser = userRepository.save(user);
        persistPreference(savedUser, "notificationsEnabled", String.valueOf(savedUser.getNotificationsEnabled()));
        persistPreference(savedUser, "budgetAlertsEnabled", String.valueOf(savedUser.getBudgetAlertsEnabled()));
        persistPreference(savedUser, "dailySummaryEnabled", String.valueOf(savedUser.getDailySummaryEnabled()));
        persistPreference(savedUser, "goalUpdatesEnabled", String.valueOf(savedUser.getGoalUpdatesEnabled()));
        return savedUser;
    }

    public User resetPreferences(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        user.setTheme("light");
        user.setLanguage("en");
        user.setNotificationsEnabled(true);
        user.setBudgetAlertsEnabled(true);
        user.setDailySummaryEnabled(true);
        user.setGoalUpdatesEnabled(true);
        User savedUser = userRepository.save(user);
        if (savedUser.getId() != null) {
            localExcelStorageService.deleteRowsByUser(savedUser.getId().toString(), List.of("Settings"));
        }
        return savedUser;
    }

    public void changePassword(User user, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void deleteUser(Long id) {
        if (id != null) {
            userRepository.deleteById(id);
        }
    }

    public void deleteAccountAndData(User user) {
        if (user == null) {
            return;
        }

        List<Account> accounts = accountRepository.findByUserOrderByCreatedAtDesc(user);
        if (!accounts.isEmpty()) {
            accountRepository.deleteAll(accounts);
        }

        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        if (!expenses.isEmpty()) {
            expenseRepository.deleteAll(expenses);
        }

        List<Income> incomes = incomeRepository.findByUserOrderByIncomeDateDesc(user);
        if (!incomes.isEmpty()) {
            incomeRepository.deleteAll(incomes);
        }

        List<Budget> budgets = budgetRepository.findByUser(user);
        if (!budgets.isEmpty()) {
            budgetRepository.deleteAll(budgets);
        }

        List<FinancialGoal> goals = financialGoalRepository.findByUserOrderByTargetDateAsc(user);
        if (!goals.isEmpty()) {
            financialGoalRepository.deleteAll(goals);
        }

        List<ImportedTransaction> imports = importedTransactionRepository.findByUserOrderByTransactionDateDesc(user);
        if (!imports.isEmpty()) {
            importedTransactionRepository.deleteAll(imports);
        }

        chatMessageRepository.deleteByUser(user);

        if (user.getId() != null) {
            localExcelStorageService.deleteRowsByUser(user.getId().toString(), USER_DATA_SHEETS);
        }

        if (user.getProfileImage() != null && !user.getProfileImage().isBlank()) {
            try {
                String fileName = user.getProfileImage().replace("/uploads/profile/", "");
                Path profileFile = Paths.get("data", "uploads", "profile", fileName);
                Files.deleteIfExists(profileFile);
            } catch (IOException ignored) {
                // ignore file cleanup failures
            }
        }

        userRepository.delete(user);
    }

    private void persistPreference(User user, String key, String value) {
        if (user == null || user.getId() == null || key == null || key.isBlank()) {
            return;
        }

        List<LocalExcelStorageService.RowData> existing = localExcelStorageService.readSheet("Settings", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) {
                return null;
            }
            if (!key.equals(getCellString(row, "SettingKey"))) {
                return null;
            }
            return mapRowToRowData(row);
        });

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (!existing.isEmpty()) {
            rowData.put("SettingValue", value);
            rowData.put("UpdatedAt", timestamp);
            String settingId = existing.get(0).get("SettingID") != null ? existing.get(0).get("SettingID").toString() : "";
            if (!settingId.isEmpty()) {
                localExcelStorageService.updateRow("Settings", "SettingID", settingId, rowData);
            }
            return;
        }

        rowData.put("SettingID", localExcelStorageService.generateId("SET"));
        rowData.put("UserID", user.getId().toString());
        rowData.put("SettingKey", key);
        rowData.put("SettingValue", value);
        rowData.put("SettingType", "user_pref");
        rowData.put("Description", "User preference setting");
        rowData.put("CreatedAt", timestamp);
        rowData.put("UpdatedAt", timestamp);
        localExcelStorageService.appendRow("Settings", rowData);
    }

    private String getCellString(Row row, String columnName) {
        if (row == null) {
            return "";
        }
        int colIndex = findColumnIndex(row.getSheet(), columnName);
        if (colIndex == -1) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        return cell != null ? cell.toString().trim() : "";
    }

    private LocalExcelStorageService.RowData mapRowToRowData(Row row) {
        Sheet sheet = row.getSheet();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return new LocalExcelStorageService.RowData();
        }
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        for (Cell cell : headerRow) {
            String colName = cell.getStringCellValue();
            Cell dataCell = row.getCell(cell.getColumnIndex());
            rowData.put(colName, dataCell != null ? dataCell.toString() : "");
        }
        return rowData;
    }

    private int findColumnIndex(Sheet sheet, String columnName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return -1;
        }
        for (Cell cell : headerRow) {
            if (columnName.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    public boolean verifyPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
}
