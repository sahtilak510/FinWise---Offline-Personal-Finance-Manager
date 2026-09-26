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
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class GoalExcelService {

    private final LocalExcelStorageService storageService;

    public GoalExcelService(LocalExcelStorageService storageService) {
        this.storageService = storageService;
    }

    public GoalRecord createGoal(User user, String goalName, String description, BigDecimal targetAmount,
                                 LocalDate targetDate, String priority) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        goalName = FinanceValidation.requireText(goalName, "Goal name", 100);
        description = FinanceValidation.optionalText(description, "Goal description", 500);
        targetAmount = FinanceValidation.requirePositiveAmount(targetAmount, "Target amount");
        targetDate = FinanceValidation.requireDate(targetDate, "Target date");
        priority = FinanceValidation.requireText(priority, "Priority", 20);

        String goalId = storageService.generateId("GOAL");
        LocalDateTime now = LocalDateTime.now();

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("GoalID", goalId);
        rowData.put("UserID", user.getId().toString());
        rowData.put("GoalName", goalName);
        rowData.put("Description", description);
        rowData.put("GoalType", "SAVINGS");
        rowData.put("TargetAmount", targetAmount);
        rowData.put("CurrentAmount", BigDecimal.ZERO);
        rowData.put("TargetDate", targetDate.toString());
        rowData.put("Priority", priority.toUpperCase());
        rowData.put("Status", "IN_PROGRESS");
        rowData.put("IsActive", "true");
        rowData.put("CreatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.appendRow("Goals", rowData);
        storageService.auditLog(user.getId().toString(), "CREATE", "Goal", goalId, "Created goal: " + goalName);

        return new GoalRecord(goalId, user.getId().toString(), goalName, description, "SAVINGS", targetAmount,
                BigDecimal.ZERO, targetDate, priority.toUpperCase(), "IN_PROGRESS", true, now, now);
    }

    public List<GoalRecord> getUserGoals(User user) {
        return storageService.readSheet("Goals", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) return null;
            return mapRowToGoalRecord(row);
        });
    }

    public Optional<GoalRecord> getGoalById(Long id) {
        return storageService.findById("Goals", "GoalID", id.toString(), row -> mapRowToGoalRecord(row));
    }

    public GoalRecord updateGoal(Long id, String goalName, String description, BigDecimal targetAmount,
                                 LocalDate targetDate, String priority) {
        goalName = FinanceValidation.requireText(goalName, "Goal name", 100);
        description = FinanceValidation.optionalText(description, "Goal description", 500);
        targetAmount = FinanceValidation.requirePositiveAmount(targetAmount, "Target amount");
        targetDate = FinanceValidation.requireDate(targetDate, "Target date");
        priority = FinanceValidation.requireText(priority, "Priority", 20);

        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("GoalName", goalName);
        rowData.put("Description", description);
        rowData.put("TargetAmount", targetAmount);
        rowData.put("TargetDate", targetDate.toString());
        rowData.put("Priority", priority.toUpperCase());
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.updateRow("Goals", "GoalID", id.toString(), rowData);
        return new GoalRecord(id.toString(), "", goalName, description, "SAVINGS", targetAmount, BigDecimal.ZERO, targetDate, priority.toUpperCase(), "IN_PROGRESS", true, now, now);
    }

    public GoalRecord updateGoalProgress(Long id, BigDecimal currentAmount) {
        currentAmount = FinanceValidation.requireNonNegativeAmount(currentAmount, "Current amount");
        LocalDateTime now = LocalDateTime.now();

        Optional<GoalRecord> goalOpt = getGoalById(id);
        if (goalOpt.isEmpty()) {
            throw new RuntimeException("Goal not found");
        }
        GoalRecord goal = goalOpt.get();

        String status = currentAmount.compareTo(goal.getTargetAmount()) >= 0 ? "COMPLETED" : "IN_PROGRESS";

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("CurrentAmount", currentAmount);
        rowData.put("Status", status);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.updateRow("Goals", "GoalID", id.toString(), rowData);
        return new GoalRecord(id.toString(), goal.getUserId(), goal.getGoalName(), goal.getDescription(),
                goal.getGoalType(), goal.getTargetAmount(), currentAmount, goal.getTargetDate(),
                goal.getPriority(), status, goal.isActive(), goal.getCreatedAt(), now);
    }

    public GoalRecord markGoalAsCompleted(Long id) {
        Optional<GoalRecord> goalOpt = getGoalById(id);
        if (goalOpt.isEmpty()) {
            throw new RuntimeException("Goal not found");
        }
        GoalRecord goal = goalOpt.get();

        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("CurrentAmount", goal.getTargetAmount());
        rowData.put("Status", "COMPLETED");
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.updateRow("Goals", "GoalID", id.toString(), rowData);
        return new GoalRecord(id.toString(), goal.getUserId(), goal.getGoalName(), goal.getDescription(),
                goal.getGoalType(), goal.getTargetAmount(), goal.getTargetAmount(), goal.getTargetDate(),
                goal.getPriority(), "COMPLETED", goal.isActive(), goal.getCreatedAt(), now);
    }

    public void deleteGoal(Long id) {
        storageService.deleteRow("Goals", "GoalID", id.toString());
    }

    private GoalRecord mapRowToGoalRecord(Row row) {
        return new GoalRecord(
                getCellString(row, "GoalID"),
                getCellString(row, "UserID"),
                getCellString(row, "GoalName"),
                getCellString(row, "Description"),
                getCellString(row, "GoalType"),
                parseBigDecimal(row, "TargetAmount"),
                parseBigDecimal(row, "CurrentAmount"),
                parseLocalDate(row, "TargetDate"),
                getCellString(row, "Priority"),
                getCellString(row, "Status"),
                "true".equalsIgnoreCase(getCellString(row, "IsActive")),
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

    public static class GoalRecord {
        private final String goalId;
        private final String userId;
        private final String goalName;
        private final String description;
        private final String goalType;
        private final BigDecimal targetAmount;
        private final BigDecimal currentAmount;
        private final LocalDate targetDate;
        private final String priority;
        private final String status;
        private final boolean isActive;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public GoalRecord(String goalId, String userId, String goalName, String description, String goalType,
                          BigDecimal targetAmount, BigDecimal currentAmount, LocalDate targetDate,
                          String priority, String status, boolean isActive, LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
            this.goalId = goalId;
            this.userId = userId;
            this.goalName = goalName;
            this.description = description;
            this.goalType = goalType;
            this.targetAmount = targetAmount;
            this.currentAmount = currentAmount;
            this.targetDate = targetDate;
            this.priority = priority;
            this.status = status;
            this.isActive = isActive;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getGoalId() { return goalId; }
        public String getUserId() { return userId; }
        public String getGoalName() { return goalName; }
        public String getDescription() { return description; }
        public String getGoalType() { return goalType; }
        public BigDecimal getTargetAmount() { return targetAmount; }
        public BigDecimal getCurrentAmount() { return currentAmount; }
        public LocalDate getTargetDate() { return targetDate; }
        public String getPriority() { return priority; }
        public String getStatus() { return status; }
        public boolean isActive() { return isActive; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }

        public BigDecimal getRemainingAmount() { return targetAmount.subtract(currentAmount); }
        public double getProgressPercentage() {
            return targetAmount.compareTo(BigDecimal.ZERO) > 0
                    ? currentAmount.divide(targetAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;
        }
        public boolean isCompleted() { return "COMPLETED".equals(status); }
        public boolean isOverdue() { return targetDate != null && targetDate.isBefore(LocalDate.now()) && !isCompleted(); }
    }
}