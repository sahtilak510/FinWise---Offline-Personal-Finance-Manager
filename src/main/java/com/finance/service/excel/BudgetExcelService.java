package com.finance.service.excel;

import com.finance.model.entity.User;
import com.finance.util.FinanceValidation;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BudgetExcelService {

    private final LocalExcelStorageService storageService;
    private final ExpenseExcelService expenseExcelService;

    public BudgetExcelService(LocalExcelStorageService storageService, ExpenseExcelService expenseExcelService) {
        this.storageService = storageService;
        this.expenseExcelService = expenseExcelService;
    }

    public BudgetRecord createBudget(User user, String category, BigDecimal limitAmount, String budgetMonth, String notes) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        category = FinanceValidation.requireText(category, "Budget category", 100);
        limitAmount = FinanceValidation.requirePositiveAmount(limitAmount, "Budget limit");
        budgetMonth = FinanceValidation.requireText(budgetMonth, "Budget month", 12);
        notes = FinanceValidation.optionalText(notes, "Budget notes", 500);

        YearMonth ym = YearMonth.parse(budgetMonth);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        String budgetId = storageService.generateId("BUD");
        LocalDateTime now = LocalDateTime.now();

        BigDecimal spentAmount = calculateSpentAmount(user, category, startDate, endDate);

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("BudgetID", budgetId);
        rowData.put("UserID", user.getId().toString());
        rowData.put("Category", category);
        rowData.put("LimitAmount", limitAmount);
        rowData.put("SpentAmount", spentAmount);
        rowData.put("BudgetMonth", budgetMonth);
        rowData.put("StartDate", startDate.toString());
        rowData.put("EndDate", endDate.toString());
        rowData.put("AlertThreshold50", "true");
        rowData.put("AlertThreshold75", "true");
        rowData.put("AlertThreshold90", "true");
        rowData.put("AlertThreshold100", "true");
        rowData.put("IsActive", "true");
        rowData.put("Notes", notes);
        rowData.put("CreatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.appendRow("Budgets", rowData);
        storageService.auditLog(user.getId().toString(), "CREATE", "Budget", budgetId, "Created budget for " + category);

        return new BudgetRecord(budgetId, user.getId().toString(), category, limitAmount, spentAmount, budgetMonth,
                startDate, endDate, true, true, true, true, true, notes, now, now);
    }

    public List<BudgetRecord> getUserActiveBudgets(User user) {
        return getUserAllBudgets(user).stream()
                .filter(budget -> budget.isActive())
                .toList();
    }

    public List<BudgetRecord> getUserAllBudgets(User user) {
        return storageService.readSheet("Budgets", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) return null;
            return mapRowToBudgetRecord(row);
        });
    }

    public Optional<BudgetRecord> getBudgetById(Long id) {
        return storageService.findById("Budgets", "BudgetID", id.toString(), row -> mapRowToBudgetRecord(row));
    }

    public Optional<BudgetRecord> getBudgetByUserAndCategoryAndMonth(User user, String category, String budgetMonth) {
        return getUserAllBudgets(user).stream()
                .filter(b -> category.equals(b.getCategory()) && budgetMonth.equals(b.getBudgetMonth()))
                .findFirst();
    }

    public BudgetRecord updateBudget(Long id, BigDecimal limitAmount, BigDecimal spentAmount, String notes) {
        limitAmount = FinanceValidation.requirePositiveAmount(limitAmount, "Budget limit");
        spentAmount = FinanceValidation.requireNonNegativeAmount(spentAmount, "Budget spent amount");
        notes = FinanceValidation.optionalText(notes, "Budget notes", 500);

        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("LimitAmount", limitAmount);
        rowData.put("SpentAmount", spentAmount);
        rowData.put("Notes", notes);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.updateRow("Budgets", "BudgetID", id.toString(), rowData);
        return new BudgetRecord(id.toString(), "", "", limitAmount, spentAmount, "", null, null, true, true, true, true, true, notes, now, now);
    }

    public void deleteBudget(Long id) {
        storageService.deleteRow("Budgets", "BudgetID", id.toString());
    }

    public BudgetRecord updateSpentAmount(Long budgetId, BigDecimal spentAmount) {
        spentAmount = FinanceValidation.requireNonNegativeAmount(spentAmount, "Budget spent amount");
        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("SpentAmount", spentAmount);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        storageService.updateRow("Budgets", "BudgetID", budgetId.toString(), rowData);
        return new BudgetRecord(budgetId.toString(), "", "", BigDecimal.ZERO, spentAmount, "", null, null, true, true, true, true, true, "", now, now);
    }

    public void toggleBudgetActive(Long id) {
        Optional<BudgetRecord> budget = getBudgetById(id);
        if (budget.isPresent()) {
            boolean newActive = !budget.get().isActive();
            LocalDateTime now = LocalDateTime.now();
            LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
            rowData.put("IsActive", String.valueOf(newActive));
            rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            storageService.updateRow("Budgets", "BudgetID", id.toString(), rowData);
        }
    }

    public void recalculateSpentAmounts(User user) {
        List<BudgetRecord> budgets = getUserAllBudgets(user);
        for (BudgetRecord budget : budgets) {
            YearMonth ym = YearMonth.parse(budget.getBudgetMonth());
            LocalDate startDate = ym.atDay(1);
            LocalDate endDate = ym.atEndOfMonth();
            BigDecimal spent = calculateSpentAmount(user, budget.getCategory(), startDate, endDate);

            LocalDateTime now = LocalDateTime.now();
            LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
            rowData.put("SpentAmount", spent);
            rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            storageService.updateRow("Budgets", "BudgetID", budget.getBudgetId(), rowData);
        }
    }

    private BigDecimal calculateSpentAmount(User user, String category, LocalDate startDate, LocalDate endDate) {
        return expenseExcelService.getExpensesByDateRange(user, startDate, endDate).stream()
                .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                .map(expense -> expense.getAmount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    private BudgetRecord mapRowToBudgetRecord(Row row) {
        return new BudgetRecord(
                getCellString(row, "BudgetID"),
                getCellString(row, "UserID"),
                getCellString(row, "Category"),
                parseBigDecimal(row, "LimitAmount"),
                parseBigDecimal(row, "SpentAmount"),
                getCellString(row, "BudgetMonth"),
                parseLocalDate(row, "StartDate"),
                parseLocalDate(row, "EndDate"),
                "true".equalsIgnoreCase(getCellString(row, "AlertThreshold50")),
                "true".equalsIgnoreCase(getCellString(row, "AlertThreshold75")),
                "true".equalsIgnoreCase(getCellString(row, "AlertThreshold90")),
                "true".equalsIgnoreCase(getCellString(row, "AlertThreshold100")),
                "true".equalsIgnoreCase(getCellString(row, "IsActive")),
                getCellString(row, "Notes"),
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

    public static class BudgetRecord {
        private final String budgetId;
        private final String userId;
        private final String category;
        private final BigDecimal limitAmount;
        private final BigDecimal spentAmount;
        private final String budgetMonth;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final boolean alertThreshold50;
        private final boolean alertThreshold75;
        private final boolean alertThreshold90;
        private final boolean alertThreshold100;
        private final boolean isActive;
        private final String notes;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public BudgetRecord(String budgetId, String userId, String category, BigDecimal limitAmount,
                            BigDecimal spentAmount, String budgetMonth, LocalDate startDate, LocalDate endDate,
                            boolean alertThreshold50, boolean alertThreshold75, boolean alertThreshold90,
                            boolean alertThreshold100, boolean isActive, String notes,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.budgetId = budgetId;
            this.userId = userId;
            this.category = category;
            this.limitAmount = limitAmount;
            this.spentAmount = spentAmount;
            this.budgetMonth = budgetMonth;
            this.startDate = startDate;
            this.endDate = endDate;
            this.alertThreshold50 = alertThreshold50;
            this.alertThreshold75 = alertThreshold75;
            this.alertThreshold90 = alertThreshold90;
            this.alertThreshold100 = alertThreshold100;
            this.isActive = isActive;
            this.notes = notes;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getBudgetId() { return budgetId; }
        public String getUserId() { return userId; }
        public String getCategory() { return category; }
        public BigDecimal getLimitAmount() { return limitAmount; }
        public BigDecimal getSpentAmount() { return spentAmount; }
        public String getBudgetMonth() { return budgetMonth; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public boolean isAlertThreshold50() { return alertThreshold50; }
        public boolean isAlertThreshold75() { return alertThreshold75; }
        public boolean isAlertThreshold90() { return alertThreshold90; }
        public boolean isAlertThreshold100() { return alertThreshold100; }
        public boolean isActive() { return isActive; }
        public String getNotes() { return notes; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }

        public BigDecimal getRemainingAmount() { return limitAmount.subtract(spentAmount); }
        public double getPercentageUsed() {
            return limitAmount.compareTo(BigDecimal.ZERO) > 0
                    ? spentAmount.divide(limitAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;
        }
        public boolean isOverBudget() { return spentAmount.compareTo(limitAmount) > 0; }
        public String getAlertLevel() {
            double pct = getPercentageUsed();
            if (pct >= 100) return "OVER";
            if (pct >= 90) return "CRITICAL";
            if (pct >= 75) return "WARNING";
            if (pct >= 50) return "CAUTION";
            return "OK";
        }
    }
}