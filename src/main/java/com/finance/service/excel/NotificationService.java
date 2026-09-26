package com.finance.service.excel;

import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.BudgetRepository;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.FinancialGoalRepository;
import com.finance.repository.IncomeRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final LocalExcelStorageService storageService;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final FinancialGoalRepository goalRepository;

    public NotificationService(LocalExcelStorageService storageService,
                               BudgetRepository budgetRepository,
                               ExpenseRepository expenseRepository,
                               IncomeRepository incomeRepository,
                               FinancialGoalRepository goalRepository) {
        this.storageService = storageService;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.goalRepository = goalRepository;
    }

    public List<Notification> checkAndGenerateNotifications(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getNotificationsEnabled())) {
            return List.of();
        }
        List<Notification> candidates = new ArrayList<>();
        if (Boolean.TRUE.equals(user.getBudgetAlertsEnabled())) {
            candidates.addAll(checkBudgetNotifications(user));
        }
        if (Boolean.TRUE.equals(user.getGoalUpdatesEnabled())) {
            candidates.addAll(checkGoalNotifications(user));
        }
        if (getBooleanSetting(user, "billRemindersEnabled", true)) {
            candidates.addAll(checkUpcomingBills(user));
        }
        if (getBooleanSetting(user, "largeExpenseAlertsEnabled", true)) {
            candidates.addAll(checkLargeExpenses(user));
        }
        if (getBooleanSetting(user, "weeklyMonthlySummaryEnabled", true)) {
            candidates.addAll(checkSummaries(user));
        }
        candidates.addAll(checkImportNotifications(user));

        List<Notification> notifications = new ArrayList<>();
        for (Notification notification : candidates) {
            if (!notificationExists(user, notification.getType(), notification.getTitle(), LocalDate.now())) {
                saveNotification(user, notification);
                notifications.add(notification);
            }
        }
        return notifications;
    }

    private List<Notification> checkBudgetNotifications(User user) {
        List<Notification> notifications = new ArrayList<>();
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        for (Budget budget : budgetRepository.findByUserAndIsActiveTrue(user)) {
            if (budget.getLimitAmount() == null || budget.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal spent = expenses.stream()
                    .filter(expense -> budget.getCategory().equalsIgnoreCase(expense.getCategory()))
                    .filter(expense -> isInBudgetMonth(expense.getExpenseDate(), budget.getBudgetMonth()))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            double percentage = spent.multiply(BigDecimal.valueOf(100))
                    .divide(budget.getLimitAmount(), 2, RoundingMode.HALF_UP).doubleValue();
            if (percentage >= 100) {
                notifications.add(notification("BUDGET_OVER", "HIGH", "Budget exceeded for " + budget.getCategory(),
                        "Spent " + money(spent) + " of " + money(budget.getLimitAmount()) + " ("
                                + String.format(java.util.Locale.ROOT, "%.0f", percentage) + "%)."));
            } else if (percentage >= 90) {
                notifications.add(notification("BUDGET_CRITICAL", "HIGH", "Budget critical for " + budget.getCategory(),
                        "You have used " + String.format(java.util.Locale.ROOT, "%.0f", percentage) + "%."));
            } else if (percentage >= 75) {
                notifications.add(notification("BUDGET_WARNING", "MEDIUM", "Budget warning for " + budget.getCategory(),
                        "You have used " + String.format(java.util.Locale.ROOT, "%.0f", percentage) + "%."));
            }
        }
        return notifications;
    }

    private List<Notification> checkGoalNotifications(User user) {
        List<Notification> notifications = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (FinancialGoal goal : goalRepository.findByUserOrderByTargetDateAsc(user)) {
            if (!"ACTIVE".equalsIgnoreCase(goal.getGoalStatus()) && !"IN_PROGRESS".equalsIgnoreCase(goal.getGoalStatus())) {
                continue;
            }
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, goal.getTargetDate());
            if (daysLeft < 0) {
                notifications.add(notification("GOAL_OVERDUE", "HIGH", "Goal overdue: " + goal.getGoalName(),
                        "The target date was " + goal.getTargetDate() + "."));
            } else if (daysLeft <= 7) {
                notifications.add(notification("GOAL_DEADLINE_SOON", "MEDIUM",
                        "Goal deadline approaching: " + goal.getGoalName(), "Due in " + daysLeft + " days."));
            }
        }
        return notifications;
    }

    private List<Notification> checkUpcomingBills(User user) {
        List<Notification> notifications = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(14);
        for (Expense expense : expenseRepository.findByUserOrderByExpenseDateDesc(user)) {
            if (expense.isRecurring() && expense.getNextOccurrence() != null
                    && !expense.getNextOccurrence().isBefore(today)
                    && !expense.getNextOccurrence().isAfter(limit)) {
                notifications.add(notification("BILL_REMINDER", "MEDIUM",
                        "Upcoming bill: " + expense.getDescription(),
                        money(expense.getAmount()) + " due on " + expense.getNextOccurrence() + "."));
            }
        }
        return notifications;
    }

    private List<Notification> checkLargeExpenses(User user) {
        BigDecimal threshold = getDecimalSetting(user, "largeExpenseThreshold", new BigDecimal("1000.00"));
        LocalDate today = LocalDate.now();
        return expenseRepository.findByUserOrderByExpenseDateDesc(user).stream()
                .filter(expense -> expense.getExpenseDate() != null && !expense.getExpenseDate().isBefore(today))
                .filter(expense -> expense.getAmount() != null && expense.getAmount().compareTo(threshold) >= 0)
                .map(expense -> notification("LARGE_EXPENSE", "HIGH",
                        "Large expense: " + expense.getDescription(),
                        money(expense.getAmount()) + " exceeded your " + money(threshold) + " alert threshold."))
                .toList();
    }

    private List<Notification> checkSummaries(User user) {
        List<Notification> notifications = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        List<Income> income = incomeRepository.findByUserOrderByIncomeDateDesc(user);

        BigDecimal weeklyExpenses = totalExpenses(expenses, weekStart, today);
        BigDecimal weeklyIncome = totalIncome(income, weekStart, today);
        notifications.add(notification("WEEKLY_SUMMARY", "LOW", "Weekly summary: " + weekStart,
                "Income " + money(weeklyIncome) + ", expenses " + money(weeklyExpenses)
                        + ", net " + money(weeklyIncome.subtract(weeklyExpenses)) + "."));

        YearMonth month = YearMonth.from(today);
        BigDecimal monthlyExpenses = totalExpenses(expenses, month.atDay(1), today);
        BigDecimal monthlyIncome = totalIncome(income, month.atDay(1), today);
        notifications.add(notification("MONTHLY_SUMMARY", "LOW", "Monthly summary: " + month,
                "Income " + money(monthlyIncome) + ", expenses " + money(monthlyExpenses)
                        + ", net " + money(monthlyIncome.subtract(monthlyExpenses)) + "."));
        return notifications;
    }

    private List<Notification> checkImportNotifications(User user) {
        List<Notification> notifications = new ArrayList<>();
        for (LocalExcelStorageService.RowData row : storageService.readSheet("Imports", candidate -> {
            if (!user.getId().toString().equals(getCellString(candidate, "UserID"))) {
                return null;
            }
            return mapRowToRowData(candidate);
        })) {
            String status = value(row, "ProcessingStatus");
            String fileName = value(row, "OriginalFilename");
            if ("FAILED".equals(status)) {
                notifications.add(notification("IMPORT_FAILED", "HIGH", "Import failed: " + fileName,
                        "The file could not be processed."));
            }
        }
        return notifications;
    }

    public void saveNotification(User user, Notification notification) {
        String notificationId = storageService.generateId("NOTIF");
        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("NotificationID", notificationId);
        rowData.put("UserID", user.getId().toString());
        rowData.put("Type", notification.getType());
        rowData.put("Priority", notification.getPriority());
        rowData.put("Title", notification.getTitle());
        rowData.put("Message", notification.getMessage());
        rowData.put("IsRead", "false");
        rowData.put("CreatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("ReadAt", "");
        storageService.appendRow("Notifications", rowData);
    }

    public List<Notification> getUserNotifications(User user, boolean unreadOnly) {
        return storageService.readSheet("Notifications", row -> {
            if (!user.getId().toString().equals(getCellString(row, "UserID"))) {
                return null;
            }
            if (unreadOnly && "true".equalsIgnoreCase(getCellString(row, "IsRead"))) {
                return null;
            }
            return mapRowToNotification(row);
        });
    }

    public void markAsRead(User user, String notificationId) {
        boolean owned = getUserNotifications(user, false).stream()
                .anyMatch(notification -> notification.getNotificationId().equals(notificationId));
        if (!owned) {
            throw new IllegalArgumentException("Notification not found");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("IsRead", "true");
        rowData.put("ReadAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        storageService.updateRow("Notifications", "NotificationID", notificationId, rowData);
    }

    public void markAllAsRead(User user) {
        LocalDateTime now = LocalDateTime.now();
        for (Notification notification : getUserNotifications(user, true)) {
            LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
            rowData.put("IsRead", "true");
            rowData.put("ReadAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            storageService.updateRow("Notifications", "NotificationID", notification.getNotificationId(), rowData);
        }
    }

    private boolean notificationExists(User user, String type, String title, LocalDate createdOnOrAfter) {
        LocalDateTime cutoff = createdOnOrAfter.atStartOfDay();
        return getUserNotifications(user, false).stream()
                .anyMatch(notification -> notification.getType().equals(type)
                        && notification.getTitle().equals(title)
                        && notification.getCreatedAt() != null
                        && !notification.getCreatedAt().isBefore(cutoff));
    }

    private boolean getBooleanSetting(User user, String key, boolean defaultValue) {
        String value = getSetting(user, key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private BigDecimal getDecimalSetting(User user, String key, BigDecimal defaultValue) {
        String value = getSetting(user, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String getSetting(User user, String key) {
        List<LocalExcelStorageService.RowData> rows = storageService.readSheet("Settings", row -> {
            if (!user.getId().toString().equals(getCellString(row, "UserID"))
                    || !key.equals(getCellString(row, "SettingKey"))) {
                return null;
            }
            return mapRowToRowData(row);
        });
        return rows.isEmpty() ? null : value(rows.get(rows.size() - 1), "SettingValue");
    }

    private boolean isInBudgetMonth(LocalDate date, String budgetMonth) {
        if (date == null || budgetMonth == null || budgetMonth.isBlank()) {
            return false;
        }
        try {
            return YearMonth.from(date).equals(YearMonth.parse(budgetMonth));
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }

    private BigDecimal totalExpenses(List<Expense> expenses, LocalDate start, LocalDate end) {
        return expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null
                        && !expense.getExpenseDate().isBefore(start)
                        && !expense.getExpenseDate().isAfter(end))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalIncome(List<Income> incomes, LocalDate start, LocalDate end) {
        return incomes.stream()
                .filter(income -> income.getIncomeDate() != null
                        && !income.getIncomeDate().isBefore(start)
                        && !income.getIncomeDate().isAfter(end))
                .map(Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private Notification notification(String type, String priority, String title, String message) {
        return new Notification(type, priority, title, message);
    }

    private String getCellString(org.apache.poi.ss.usermodel.Row row, String columnName) {
        int colIndex = findColumnIndex(row.getSheet(), columnName);
        if (colIndex == -1) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        return cell != null ? cell.toString().trim() : "";
    }

    private LocalExcelStorageService.RowData mapRowToRowData(org.apache.poi.ss.usermodel.Row row) {
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        Sheet sheet = row.getSheet();
        org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return rowData;
        }
        for (Cell cell : headerRow) {
            String colName = cell.getStringCellValue();
            Cell dataCell = row.getCell(cell.getColumnIndex());
            rowData.put(colName, dataCell != null ? dataCell.toString() : "");
        }
        return rowData;
    }

    private int findColumnIndex(Sheet sheet, String columnName) {
        org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
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

    private Notification mapRowToNotification(org.apache.poi.ss.usermodel.Row row) {
        return new Notification(
                getCellString(row, "NotificationID"),
                getCellString(row, "Type"),
                getCellString(row, "Priority"),
                getCellString(row, "Title"),
                getCellString(row, "Message"),
                "true".equalsIgnoreCase(getCellString(row, "IsRead")),
                parseLocalDateTime(row, "CreatedAt"),
                parseLocalDateTime(row, "ReadAt")
        );
    }

    private LocalDateTime parseLocalDateTime(org.apache.poi.ss.usermodel.Row row, String columnName) {
        String value = getCellString(row, columnName);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private String value(LocalExcelStorageService.RowData row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString().trim();
    }

    public static class Notification {
        private final String notificationId;
        private final String type;
        private final String priority;
        private final String title;
        private final String message;
        private final boolean isRead;
        private final LocalDateTime createdAt;
        private final LocalDateTime readAt;

        public Notification(String type, String priority, String title, String message) {
            this.notificationId = "";
            this.type = type;
            this.priority = priority;
            this.title = title;
            this.message = message;
            this.isRead = false;
            this.createdAt = LocalDateTime.now();
            this.readAt = null;
        }

        public Notification(String notificationId, String type, String priority, String title,
                            String message, boolean isRead, LocalDateTime createdAt, LocalDateTime readAt) {
            this.notificationId = notificationId;
            this.type = type;
            this.priority = priority;
            this.title = title;
            this.message = message;
            this.isRead = isRead;
            this.createdAt = createdAt;
            this.readAt = readAt;
        }

        public String getNotificationId() { return notificationId; }
        public String getType() { return type; }
        public String getPriority() { return priority; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public boolean isRead() { return isRead; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getReadAt() { return readAt; }
    }
}
