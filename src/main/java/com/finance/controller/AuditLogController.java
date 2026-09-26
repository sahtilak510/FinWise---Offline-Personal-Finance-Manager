package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.excel.LocalExcelStorageService;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/audit")
@AllArgsConstructor
public class AuditLogController {

    private final UserRepository userRepository;
    private final LocalExcelStorageService storageService;

    @GetMapping
    public String viewAuditLog(Authentication authentication, Model model,
                              @RequestParam(value = "action", required = false) String action,
                              @RequestParam(value = "entity", required = false) String entity) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<AuditEntry> entries = getFilteredEntries(user, action, entity);

        model.addAttribute("user", user);
        model.addAttribute("entries", entries);
        model.addAttribute("selectedAction", action);
        model.addAttribute("selectedEntity", entity);
        model.addAttribute("actionTypes", getActionTypes());
        model.addAttribute("entityTypes", getEntityTypes());

        return "audit";
    }

    private List<AuditEntry> getFilteredEntries(User user, String action, String entity) {
        List<AuditEntry> all = storageService.readSheet("Audit", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId) && !rowUserId.isBlank()) {
                return null;
            }
            return mapRowToAuditEntry(row);
        });

        return all.stream()
                .filter(e -> action == null || action.isEmpty() || action.equalsIgnoreCase(e.action))
                .filter(e -> entity == null || entity.isEmpty() || entity.equalsIgnoreCase(e.entity))
                .sorted(Comparator.comparing((AuditEntry e) -> e.timestamp != null ? e.timestamp : LocalDateTime.MIN).reversed())
                .toList();
    }

    private List<String> getActionTypes() {
        Set<String> actions = new LinkedHashSet<>();
        List<AuditEntry> all = storageService.readSheet("Audit", row -> mapRowToAuditEntry(row));
        for (AuditEntry entry : all) {
            if (entry.action != null && !entry.action.isBlank()) {
                actions.add(entry.action);
            }
        }
        return new ArrayList<>(actions);
    }

    private List<String> getEntityTypes() {
        Set<String> entities = new LinkedHashSet<>();
        List<AuditEntry> all = storageService.readSheet("Audit", row -> mapRowToAuditEntry(row));
        for (AuditEntry entry : all) {
            if (entry.entity != null && !entry.entity.isBlank()) {
                entities.add(entry.entity);
            }
        }
        return new ArrayList<>(entities);
    }

    private AuditEntry mapRowToAuditEntry(Row row) {
        String timestampStr = getCellString(row, "Timestamp");
        LocalDateTime timestamp = null;
        try {
            if (!timestampStr.isEmpty()) {
                timestamp = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (Exception ignored) {}

        return new AuditEntry(
                getCellString(row, "AuditID"),
                getCellString(row, "UserID"),
                getCellString(row, "Action"),
                getCellString(row, "Entity"),
                getCellString(row, "RecordID"),
                timestamp,
                getCellString(row, "Details"),
                getCellString(row, "IPAddress")
        );
    }

    private String getCellString(Row row, String columnName) {
        int colIndex = findColumnIndex(row.getSheet(), columnName);
        if (colIndex == -1) return "";
        Cell cell = row.getCell(colIndex);
        return cell != null ? cell.toString().trim() : "";
    }

    private int findColumnIndex(Sheet sheet, String columnName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return -1;
        for (Cell cell : headerRow) {
            if (columnName.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    public record AuditEntry(
            String auditId,
            String userId,
            String action,
            String entity,
            String recordId,
            LocalDateTime timestamp,
            String details,
            String ipAddress
    ) {}
}
