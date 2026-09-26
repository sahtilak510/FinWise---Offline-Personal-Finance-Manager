package com.finance.service.excel;

import com.finance.model.entity.User;
import com.finance.util.FinanceValidation;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class IncomeExcelService {

    private final LocalExcelStorageService storageService;

    public IncomeExcelService(LocalExcelStorageService storageService) {
        this.storageService = storageService;
    }

    public IncomeRecord addIncome(User user, String description, BigDecimal amount, LocalDate incomeDate,
                                  String category, String incomeSource, String notes) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        description = FinanceValidation.requireText(description, "Income description", 200);
        amount = FinanceValidation.requirePositiveAmount(amount, "Income amount");
        incomeDate = FinanceValidation.requireDate(incomeDate, "Income date");
        category = FinanceValidation.requireText(category, "Income category", 100);
        incomeSource = FinanceValidation.requireText(incomeSource, "Income source", 150);
        notes = FinanceValidation.optionalText(notes, "Income notes", 500);

        String incomeId = storageService.generateId("INC");
        LocalDateTime now = LocalDateTime.now();

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("IncomeID", incomeId);
        rowData.put("UserID", user.getId().toString());
        rowData.put("Description", description);
        rowData.put("Amount", amount);
        rowData.put("IncomeDate", incomeDate.toString());
        rowData.put("Category", category);
        rowData.put("IncomeSource", incomeSource);
        rowData.put("Notes", notes);
        rowData.put("IsRecurring", "false");
        rowData.put("Frequency", "");
        rowData.put("NextOccurrence", "");
        rowData.put("LastOccurrence", "");
        rowData.put("Reference", "");
        rowData.put("AttachmentPath", "");
        rowData.put("CreatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.appendRow("Income", rowData);
        storageService.auditLog(user.getId().toString(), "CREATE", "Income", incomeId, "Added income: " + description);

        return new IncomeRecord(incomeId, user.getId().toString(), description, amount, incomeDate, category, incomeSource, notes, false, "", null, null, "", "", now, now);
    }

    public List<IncomeRecord> getUserIncome(User user) {
        return storageService.readSheet("Income", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) return null;
            return mapRowToIncomeRecord(row);
        });
    }

    public List<IncomeRecord> getIncomeByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return getUserIncome(user).stream()
                .filter(i -> !i.getIncomeDate().isBefore(startDate) && !i.getIncomeDate().isAfter(endDate))
                .toList();
    }

    public List<IncomeRecord> getIncomeByCategory(User user, String category) {
        return getUserIncome(user).stream()
                .filter(i -> category.equalsIgnoreCase(i.getCategory()))
                .toList();
    }

    public BigDecimal getTotalIncomeByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return getIncomeByDateRange(user, startDate, endDate).stream()
                .map(income -> income.getAmount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    public BigDecimal getTotalIncomeByCategory(User user, String category) {
        return getIncomeByCategory(user, category).stream()
                .map(income -> income.getAmount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    public Optional<IncomeRecord> getIncomeById(Long id) {
        return getIncomeById(id.toString());
    }

    public Optional<IncomeRecord> getIncomeById(String id) {
        return storageService.findById("Income", "IncomeID", id, row -> mapRowToIncomeRecord(row));
    }

    public IncomeRecord updateIncome(Long id, String description, BigDecimal amount, LocalDate incomeDate,
                                     String category, String incomeSource, String notes) {
        description = FinanceValidation.requireText(description, "Income description", 200);
        amount = FinanceValidation.requirePositiveAmount(amount, "Income amount");
        incomeDate = FinanceValidation.requireDate(incomeDate, "Income date");
        category = FinanceValidation.requireText(category, "Income category", 100);
        incomeSource = FinanceValidation.requireText(incomeSource, "Income source", 150);
        notes = FinanceValidation.optionalText(notes, "Income notes", 500);

        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("Description", description);
        rowData.put("Amount", amount);
        rowData.put("IncomeDate", incomeDate.toString());
        rowData.put("Category", category);
        rowData.put("IncomeSource", incomeSource);
        rowData.put("Notes", notes);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.updateRow("Income", "IncomeID", id.toString(), rowData);
        return new IncomeRecord(id.toString(), "", description, amount, incomeDate, category, incomeSource, notes, false, "", null, null, "", "", now, now);
    }

    public IncomeRecord updateIncome(String id, String description, BigDecimal amount, LocalDate incomeDate,
                                     String category, String incomeSource, String notes) {
        IncomeRecord updated = updateIncomeValues(id, description, amount, incomeDate, category, incomeSource, notes);
        return updated;
    }

    public void deleteIncome(Long id) {
        deleteIncome(id.toString());
    }

    public void deleteIncome(String id) {
        storageService.deleteRow("Income", "IncomeID", id);
    }

    private IncomeRecord updateIncomeValues(String id, String description, BigDecimal amount, LocalDate incomeDate,
                                             String category, String incomeSource, String notes) {
        description = FinanceValidation.requireText(description, "Income description", 200);
        amount = FinanceValidation.requirePositiveAmount(amount, "Income amount");
        incomeDate = FinanceValidation.requireDate(incomeDate, "Income date");
        category = FinanceValidation.requireText(category, "Income category", 100);
        incomeSource = FinanceValidation.requireText(incomeSource, "Income source", 150);
        notes = FinanceValidation.optionalText(notes, "Income notes", 500);
        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("Description", description);
        rowData.put("Amount", amount);
        rowData.put("IncomeDate", incomeDate.toString());
        rowData.put("Category", category);
        rowData.put("IncomeSource", incomeSource);
        rowData.put("Notes", notes);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        storageService.updateRow("Income", "IncomeID", id, rowData);
        return new IncomeRecord(id, "", description, amount, incomeDate, category, incomeSource, notes, false, "", null, null, "", "", now, now);
    }

    private IncomeRecord mapRowToIncomeRecord(Row row) {
        return new IncomeRecord(
                getCellString(row, "IncomeID"),
                getCellString(row, "UserID"),
                getCellString(row, "Description"),
                parseBigDecimal(row, "Amount"),
                parseLocalDate(row, "IncomeDate"),
                getCellString(row, "Category"),
                getCellString(row, "IncomeSource"),
                getCellString(row, "Notes"),
                "true".equalsIgnoreCase(getCellString(row, "IsRecurring")),
                getCellString(row, "Frequency"),
                parseLocalDate(row, "NextOccurrence"),
                parseLocalDate(row, "LastOccurrence"),
                getCellString(row, "Reference"),
                getCellString(row, "AttachmentPath"),
                parseLocalDateTime(row, "CreatedAt"),
                parseLocalDateTime(row, "UpdatedAt")
        );
    }

    private String getCellString(Row row, String columnName) {
        int colIndex = findColumnIndex(row.getSheet(), columnName);
        if (colIndex == -1) return "";
        Cell cell = row.getCell(colIndex);
        return cell != null ? cell.toString().trim() : "";
    }

    private BigDecimal parseBigDecimal(Row row, String columnName) {
        String val = getCellString(row, columnName);
        try {
            return val.isEmpty() ? BigDecimal.ZERO : new BigDecimal(val);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseLocalDate(Row row, String columnName) {
        String val = getCellString(row, columnName);
        if (val.isEmpty()) return null;
        try {
            return LocalDate.parse(val);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(Row row, String columnName) {
        String val = getCellString(row, columnName);
        if (val.isEmpty()) return null;
        try {
            return LocalDateTime.parse(val);
        } catch (Exception ignored) {
            return null;
        }
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

    public static class IncomeRecord {
        private final String incomeId;
        private final String userId;
        private final String description;
        private final BigDecimal amount;
        private final LocalDate incomeDate;
        private final String category;
        private final String incomeSource;
        private final String notes;
        private final boolean isRecurring;
        private final String frequency;
        private final LocalDate nextOccurrence;
        private final LocalDate lastOccurrence;
        private final String reference;
        private final String attachmentPath;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public IncomeRecord(String incomeId, String userId, String description, BigDecimal amount,
                            LocalDate incomeDate, String category, String incomeSource, String notes,
                            boolean isRecurring, String frequency, LocalDate nextOccurrence,
                            LocalDate lastOccurrence, String reference, String attachmentPath,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.incomeId = incomeId;
            this.userId = userId;
            this.description = description;
            this.amount = amount;
            this.incomeDate = incomeDate;
            this.category = category;
            this.incomeSource = incomeSource;
            this.notes = notes;
            this.isRecurring = isRecurring;
            this.frequency = frequency;
            this.nextOccurrence = nextOccurrence;
            this.lastOccurrence = lastOccurrence;
            this.reference = reference;
            this.attachmentPath = attachmentPath;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getIncomeId() { return incomeId; }
        public String getUserId() { return userId; }
        public String getDescription() { return description; }
        public BigDecimal getAmount() { return amount; }
        public LocalDate getIncomeDate() { return incomeDate; }
        public String getCategory() { return category; }
        public String getIncomeSource() { return incomeSource; }
        public String getNotes() { return notes; }
        public boolean isRecurring() { return isRecurring; }
        public String getFrequency() { return frequency; }
        public LocalDate getNextOccurrence() { return nextOccurrence; }
        public LocalDate getLastOccurrence() { return lastOccurrence; }
        public String getReference() { return reference; }
        public String getAttachmentPath() { return attachmentPath; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}