package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.AppLockService;
import com.finance.service.UserService;
import com.finance.service.excel.BackupRestoreService;
import com.finance.service.excel.LocalExcelStorageService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/settings")
@AllArgsConstructor
public class SettingsController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AppLockService appLockService;
    private final LocalExcelStorageService storageService;
    private final BackupRestoreService backupRestoreService;

    @GetMapping
    public String viewSettings(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, String> settings = getUserSettings(user);
        model.addAttribute("user", user);
        model.addAttribute("settings", settings);
        model.addAttribute("appLockEnabled", appLockService.isEnabled(user));
        model.addAttribute("backups", backupRestoreService.listBackups());
        model.addAttribute("backupDirectory", backupRestoreService.getBackupDirectory().toString());

        return "settings";
    }

    @PostMapping("/update")
    public String updateSettings(Authentication authentication,
                               @RequestParam(required = false) String theme,
                               @RequestParam(required = false) String currency,
                               @RequestParam(required = false) String dateFormat,
                               @RequestParam(required = false) String language,
                               @RequestParam(required = false, defaultValue = "true") boolean notificationsEnabled,
                               @RequestParam(required = false, defaultValue = "true") boolean budgetAlertsEnabled,
                               @RequestParam(required = false, defaultValue = "true") boolean dailySummaryEnabled,
                               @RequestParam(required = false, defaultValue = "true") boolean goalUpdatesEnabled,
                               @RequestParam(required = false, defaultValue = "true") boolean billRemindersEnabled,
                               @RequestParam(required = false, defaultValue = "true") boolean largeExpenseAlertsEnabled,
                               @RequestParam(required = false, defaultValue = "true") boolean weeklyMonthlySummaryEnabled,
                               @RequestParam(required = false, defaultValue = "1000.00") BigDecimal largeExpenseThreshold,
                               @RequestParam(required = false) String fullName,
                               @RequestParam(required = false) String email,
                               RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String normalizedTheme = theme != null && !theme.isBlank() ? theme.trim().toLowerCase() : user.getTheme();
        // Light or Dark only — legacy 'auto' migrates to 'light'.
        if (!"dark".equals(normalizedTheme) && !"light".equals(normalizedTheme)) {
            normalizedTheme = "light";
        }
        String normalizedLanguage = language != null && !language.isBlank() ? language.trim().toLowerCase() : user.getLanguage();
        BigDecimal normalizedThreshold = largeExpenseThreshold;
        if (normalizedThreshold == null || normalizedThreshold.compareTo(BigDecimal.ZERO) <= 0
                || normalizedThreshold.compareTo(new BigDecimal("1000000")) > 0) {
            normalizedThreshold = new BigDecimal(getUserSettings(user).getOrDefault(
                    "largeExpenseThreshold", "1000.00"));
        }
        // Global language system: en (default), hi, ta, ne. Fallback to English.
        if (!"en".equals(normalizedLanguage) && !"hi".equals(normalizedLanguage)
                && !"ta".equals(normalizedLanguage) && !"ne".equals(normalizedLanguage)) {
            normalizedLanguage = "en";
        }

        userService.updateTheme(user, normalizedTheme);
        userService.updateLanguage(user, normalizedLanguage);
        userService.updateNotificationPreferences(user, Map.of(
                "notificationsEnabled", notificationsEnabled,
                "budgetAlerts", budgetAlertsEnabled,
                "dailySummary", dailySummaryEnabled,
                "goalUpdates", goalUpdatesEnabled
        ));

        saveSetting(user, "theme", normalizedTheme);
        saveSetting(user, "currency", currency != null ? currency : "USD");
        saveSetting(user, "dateFormat", dateFormat != null ? dateFormat : "MM/dd/yyyy");
        saveSetting(user, "language", normalizedLanguage);
        saveSetting(user, "notificationsEnabled", String.valueOf(notificationsEnabled));
        saveSetting(user, "budgetAlertsEnabled", String.valueOf(budgetAlertsEnabled));
        saveSetting(user, "dailySummaryEnabled", String.valueOf(dailySummaryEnabled));
        saveSetting(user, "goalUpdatesEnabled", String.valueOf(goalUpdatesEnabled));
        saveSetting(user, "billRemindersEnabled", String.valueOf(billRemindersEnabled));
        saveSetting(user, "largeExpenseAlertsEnabled", String.valueOf(largeExpenseAlertsEnabled));
        saveSetting(user, "weeklyMonthlySummaryEnabled", String.valueOf(weeklyMonthlySummaryEnabled));
        saveSetting(user, "largeExpenseThreshold", normalizedThreshold.toPlainString());

        redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully!");
        return "redirect:/settings";
    }

    @PostMapping("/security/passcode")
    public String setPasscode(Authentication authentication,
                              @RequestParam String passcode,
                              @RequestParam String confirmPasscode,
                              RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passcode.equals(confirmPasscode)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Passcodes do not match.");
            return "redirect:/settings";
        }
        try {
            appLockService.setPasscode(user, passcode);
            redirectAttributes.addFlashAttribute("successMessage", "Application passcode enabled.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/security/passcode/disable")
    public String disablePasscode(Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        appLockService.disable(user);
        redirectAttributes.addFlashAttribute("successMessage", "Application passcode disabled.");
        return "redirect:/settings";
    }

    @PostMapping("/reset")
    @ResponseBody
    public Map<String, Object> resetSettings(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        userService.resetPreferences(user);
        return Map.of("success", true, "message", "Settings reset to defaults!");
    }

    @PostMapping("/backup")
    public String createBackup(Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            backupRestoreService.createBackup(user.getId().toString());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Encrypted backup created successfully in " + backupRestoreService.getBackupDirectory() + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Backup failed: " + e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/backups/restore/{fileName}")
    public String restoreBackup(Authentication authentication, @PathVariable String fileName,
                                RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            backupRestoreService.restoreBackup(fileName, user.getId().toString());
            redirectAttributes.addFlashAttribute("successMessage", "Backup restored. A safety backup was created first.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Restore failed: " + e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/backups/delete/{fileName}")
    public String deleteBackup(Authentication authentication, @PathVariable String fileName,
                               RedirectAttributes redirectAttributes) {
        userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            backupRestoreService.deleteBackup(fileName);
            redirectAttributes.addFlashAttribute("successMessage", "Backup deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Delete failed: " + e.getMessage());
        }
        return "redirect:/settings";
    }

    private Map<String, String> getUserSettings(User user) {
        String userTheme = user.getTheme() == null ? "" : user.getTheme().trim().toLowerCase();
        // Light or Dark only — legacy 'auto' migrates to 'light'.
        if (!"light".equals(userTheme) && !"dark".equals(userTheme)) {
            userTheme = "light";
        }
        String userLanguage = user.getLanguage() == null ? "" : user.getLanguage().trim().toLowerCase();
        if (!"en".equals(userLanguage) && !"hi".equals(userLanguage)
                && !"ta".equals(userLanguage) && !"ne".equals(userLanguage)) {
            userLanguage = "en";
        }
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("theme", userTheme);
        defaults.put("currency", "USD");
        defaults.put("dateFormat", "MM/dd/yyyy");
        defaults.put("language", userLanguage);
        defaults.put("notificationsEnabled", "true");
        defaults.put("budgetAlertsEnabled", "true");
        defaults.put("dailySummaryEnabled", "true");
        defaults.put("goalUpdatesEnabled", "true");
        defaults.put("billRemindersEnabled", "true");
        defaults.put("largeExpenseAlertsEnabled", "true");
        defaults.put("weeklyMonthlySummaryEnabled", "true");
        defaults.put("largeExpenseThreshold", "1000.00");

        List<LocalExcelStorageService.RowData> rows = storageService.readSheet("Settings", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) return null;
            return mapRowToRowData(row);
        });

        for (LocalExcelStorageService.RowData row : rows) {
            String key = row.get("SettingKey") != null ? row.get("SettingKey").toString() : "";
            String value = row.get("SettingValue") != null ? row.get("SettingValue").toString() : "";
            if (!key.isEmpty()) {
                defaults.put(key, value);
            }
        }

        return defaults;
    }

    private void saveSetting(User user, String key, String value) {
        List<LocalExcelStorageService.RowData> existing = storageService.readSheet("Settings", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) return null;
            if (!key.equals(getCellString(row, "SettingKey"))) return null;
            return mapRowToRowData(row);
        });

        if (!existing.isEmpty()) {
            LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
            rowData.put("SettingValue", value);
            rowData.put("UpdatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            String settingId = existing.get(0).get("SettingID") != null ?
                    existing.get(0).get("SettingID").toString() : "";
            storageService.updateRow("Settings", "SettingID", settingId, rowData);
        } else {
            String settingId = storageService.generateId("SET");
            LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
            rowData.put("SettingID", settingId);
            rowData.put("UserID", user.getId().toString());
            rowData.put("SettingKey", key);
            rowData.put("SettingValue", value);
            rowData.put("SettingType", "user_pref");
            rowData.put("Description", "User preference setting");
            rowData.put("CreatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            rowData.put("UpdatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            storageService.appendRow("Settings", rowData);
        }
    }

    private String getCellString(org.apache.poi.ss.usermodel.Row row, String columnName) {
        int colIndex = findColumnIndex(row.getSheet(), columnName);
        if (colIndex == -1) return "";
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIndex);
        return cell != null ? cell.toString().trim() : "";
    }

    private LocalExcelStorageService.RowData mapRowToRowData(org.apache.poi.ss.usermodel.Row row) {
        org.apache.poi.ss.usermodel.Sheet sheet = row.getSheet();
        org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
        if (headerRow == null) return new LocalExcelStorageService.RowData();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        for (org.apache.poi.ss.usermodel.Cell cell : headerRow) {
            String colName = cell.getStringCellValue();
            org.apache.poi.ss.usermodel.Cell dataCell = row.getCell(cell.getColumnIndex());
            rowData.put(colName, dataCell != null ? dataCell.toString() : "");
        }
        return rowData;
    }

    private int findColumnIndex(org.apache.poi.ss.usermodel.Sheet sheet, String columnName) {
        org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
        if (headerRow == null) return -1;
        for (org.apache.poi.ss.usermodel.Cell cell : headerRow) {
            if (columnName.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }
}
