package com.finance.service;

import com.finance.model.entity.User;
import com.finance.service.excel.LocalExcelStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AppLockService {

    private final LocalExcelStorageService storageService;
    private final PasswordEncoder passwordEncoder;

    public AppLockService(LocalExcelStorageService storageService, PasswordEncoder passwordEncoder) {
        this.storageService = storageService;
        this.passwordEncoder = passwordEncoder;
    }

    public void setPasscode(User user, String passcode) {
        if (passcode == null || !passcode.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("Passcode must contain 4 to 8 digits");
        }
        saveSetting(user, "appPasscodeHash", passwordEncoder.encode(passcode));
        saveSetting(user, "appLockEnabled", "true");
    }

    public void disable(User user) {
        saveSetting(user, "appLockEnabled", "false");
        saveSetting(user, "appPasscodeHash", "");
    }

    public boolean isEnabled(User user) {
        return Boolean.parseBoolean(getSetting(user, "appLockEnabled"))
                && !getSetting(user, "appPasscodeHash").isBlank();
    }

    public boolean verify(User user, String passcode) {
        String hash = getSetting(user, "appPasscodeHash");
        return !hash.isBlank() && passwordEncoder.matches(passcode == null ? "" : passcode, hash);
    }

    public boolean isUnlocked(User user, HttpSession session) {
        return !isEnabled(user) || session != null
                && Boolean.TRUE.equals(session.getAttribute(sessionKey(user)));
    }

    public void unlock(User user, HttpSession session) {
        session.setAttribute(sessionKey(user), true);
    }

    public void lock(User user, HttpSession session) {
        session.removeAttribute(sessionKey(user));
    }

    private void saveSetting(User user, String key, String value) {
        var existing = storageService.readSheet("Settings", row -> {
            if (!user.getId().toString().equals(getCellString(row, "UserID"))
                    || !key.equals(getCellString(row, "SettingKey"))) {
                return null;
            }
            var rowData = new LocalExcelStorageService.RowData();
            rowData.put("SettingID", getCellString(row, "SettingID"));
            rowData.put("SettingValue", getCellString(row, "SettingValue"));
            return rowData;
        });
        LocalDateTime now = LocalDateTime.now();
        if (!existing.isEmpty()) {
            LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
            rowData.put("SettingValue", value);
            rowData.put("UpdatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            storageService.updateRow("Settings", "SettingID", existing.get(existing.size() - 1).get("SettingID").toString(), rowData);
            return;
        }
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("SettingID", storageService.generateId("SET"));
        rowData.put("UserID", user.getId().toString());
        rowData.put("SettingKey", key);
        rowData.put("SettingValue", value);
        rowData.put("SettingType", "security");
        rowData.put("Description", "Application lock");
        rowData.put("CreatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        storageService.appendRow("Settings", rowData);
    }

    private String getSetting(User user, String key) {
        var rows = storageService.readSheet("Settings", row -> {
            if (!user.getId().toString().equals(getCellString(row, "UserID"))
                    || !key.equals(getCellString(row, "SettingKey"))) {
                return null;
            }
            return getCellString(row, "SettingValue");
        });
        return rows.isEmpty() ? "" : rows.get(rows.size() - 1);
    }

    private String sessionKey(User user) {
        return "finwise_app_unlocked_" + user.getId();
    }

    private String getCellString(org.apache.poi.ss.usermodel.Row row, String columnName) {
        int colIndex = -1;
        var header = row.getSheet().getRow(0);
        if (header == null) {
            return "";
        }
        for (var cell : header) {
            if (columnName.equals(cell.getStringCellValue())) {
                colIndex = cell.getColumnIndex();
                break;
            }
        }
        if (colIndex == -1) {
            return "";
        }
        var cell = row.getCell(colIndex);
        return cell == null ? "" : cell.toString().trim();
    }
}
