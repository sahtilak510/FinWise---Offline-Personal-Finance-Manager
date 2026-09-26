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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ExpenseExcelService {

    private final LocalExcelStorageService storageService;

    public ExpenseExcelService(LocalExcelStorageService storageService) {
        this.storageService = storageService;
    }

    public ExpenseRecord addExpense(User user, String description, BigDecimal amount, LocalDate expenseDate,
                                    LocalTime expenseTime, String category, String subcategory, String merchant,
                                    String paymentMethod, String account, String notes,
                                    boolean isRecurring, String frequency, String reference, String attachmentPath) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        description = FinanceValidation.requireText(description, "Expense description", 200);
        amount = FinanceValidation.requirePositiveAmount(amount, "Expense amount");
        expenseDate = FinanceValidation.requireDate(expenseDate, "Expense date");
        category = FinanceValidation.requireText(category, "Expense category", 100);
        paymentMethod = FinanceValidation.requireText(paymentMethod, "Payment method", 100);
        notes = FinanceValidation.optionalText(notes, "Expense notes", 500);
        subcategory = subcategory != null ? subcategory.trim() : "";
        merchant = merchant != null ? merchant.trim() : "";
        account = account != null ? account.trim() : "";
        reference = reference != null ? reference.trim() : "";
        attachmentPath = attachmentPath != null ? attachmentPath.trim() : "";
        frequency = frequency != null ? frequency.trim().toUpperCase() : "";

        if (isRecurring && frequency.isEmpty()) {
            throw new IllegalArgumentException("Frequency is required for recurring expenses");
        }

        String expenseId = storageService.generateId("EXP");
        LocalDateTime now = LocalDateTime.now();

        LocalDate nextOccurrence = null;
        if (isRecurring && !frequency.isEmpty()) {
            nextOccurrence = calculateNextOccurrence(expenseDate, frequency);
        }

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("ExpenseID", expenseId);
        rowData.put("UserID", user.getId().toString());
        rowData.put("Description", description);
        rowData.put("Amount", amount);
        rowData.put("ExpenseDate", expenseDate.toString());
        rowData.put("ExpenseTime", expenseTime != null ? expenseTime.toString() : "");
        rowData.put("Category", category);
        rowData.put("Subcategory", subcategory);
        rowData.put("Merchant", merchant);
        rowData.put("PaymentMethod", paymentMethod);
        rowData.put("Account", account);
        rowData.put("Notes", notes);
        rowData.put("IsRecurring", String.valueOf(isRecurring));
        rowData.put("Frequency", frequency);
        rowData.put("NextOccurrence", nextOccurrence != null ? nextOccurrence.toString() : "");
        rowData.put("LastOccurrence", "");
        rowData.put("Reference", reference);
        rowData.put("AttachmentPath", attachmentPath);
        rowData.put("ReceiptImage", "");
        rowData.put("CreatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.appendRow("Expenses", rowData);
        storageService.auditLog(user.getId().toString(), "CREATE", "Expense", expenseId, "Added expense: " + description);

        return new ExpenseRecord(expenseId, user.getId().toString(), description, amount, expenseDate, expenseTime,
                category, subcategory, merchant, paymentMethod, account, notes, isRecurring, frequency,
                nextOccurrence, null, reference, attachmentPath, now, now);
    }

    public List<ExpenseRecord> getUserExpenses(User user) {
        return storageService.readSheet("Expenses", row -> {
            String rowUserId = getCellString(row, "UserID");
            if (!user.getId().toString().equals(rowUserId)) return null;
            return mapRowToExpenseRecord(row);
        });
    }

    public List<ExpenseRecord> getExpensesByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return getUserExpenses(user).stream()
                .filter(e -> !e.getExpenseDate().isBefore(startDate) && !e.getExpenseDate().isAfter(endDate))
                .toList();
    }

    public List<ExpenseRecord> getExpensesByCategory(User user, String category) {
        return getUserExpenses(user).stream()
                .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                .toList();
    }

    public BigDecimal getTotalExpensesByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return getExpensesByDateRange(user, startDate, endDate).stream()
                .map(expense -> expense.getAmount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    public BigDecimal getTotalExpensesByCategory(User user, String category) {
        return getExpensesByCategory(user, category).stream()
                .map(expense -> expense.getAmount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    public Optional<ExpenseRecord> getExpenseById(Long id) {
        return getExpenseById(id.toString());
    }

    public Optional<ExpenseRecord> getExpenseById(String id) {
        return storageService.findById("Expenses", "ExpenseID", id, row -> mapRowToExpenseRecord(row));
    }

    public ExpenseRecord updateExpense(Long id, String description, BigDecimal amount, LocalDate expenseDate,
                                       LocalTime expenseTime, String category, String subcategory, String merchant,
                                       String paymentMethod, String account, String notes,
                                       boolean isRecurring, String frequency, String reference, String attachmentPath) {
        description = FinanceValidation.requireText(description, "Expense description", 200);
        amount = FinanceValidation.requirePositiveAmount(amount, "Expense amount");
        expenseDate = FinanceValidation.requireDate(expenseDate, "Expense date");
        category = FinanceValidation.requireText(category, "Expense category", 100);
        paymentMethod = FinanceValidation.requireText(paymentMethod, "Payment method", 100);
        notes = FinanceValidation.optionalText(notes, "Expense notes", 500);
        subcategory = subcategory != null ? subcategory.trim() : "";
        merchant = merchant != null ? merchant.trim() : "";
        account = account != null ? account.trim() : "";
        reference = reference != null ? reference.trim() : "";
        attachmentPath = attachmentPath != null ? attachmentPath.trim() : "";
        frequency = frequency != null ? frequency.trim().toUpperCase() : "";

        if (isRecurring && frequency.isEmpty()) {
            throw new IllegalArgumentException("Frequency is required for recurring expenses");
        }

        LocalDate nextOccurrence = null;
        if (isRecurring && !frequency.isEmpty()) {
            nextOccurrence = calculateNextOccurrence(expenseDate, frequency);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("Description", description);
        rowData.put("Amount", amount);
        rowData.put("ExpenseDate", expenseDate.toString());
        rowData.put("ExpenseTime", expenseTime != null ? expenseTime.toString() : "");
        rowData.put("Category", category);
        rowData.put("Subcategory", subcategory);
        rowData.put("Merchant", merchant);
        rowData.put("PaymentMethod", paymentMethod);
        rowData.put("Account", account);
        rowData.put("Notes", notes);
        rowData.put("IsRecurring", String.valueOf(isRecurring));
        rowData.put("Frequency", frequency);
        rowData.put("NextOccurrence", nextOccurrence != null ? nextOccurrence.toString() : "");
        rowData.put("Reference", reference);
        rowData.put("AttachmentPath", attachmentPath);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.updateRow("Expenses", "ExpenseID", id.toString(), rowData);
        return new ExpenseRecord(id.toString(), "", description, amount, expenseDate, expenseTime,
                category, subcategory, merchant, paymentMethod, account, notes, isRecurring, frequency,
                nextOccurrence, null, reference, attachmentPath, now, now);
    }

    public ExpenseRecord updateExpense(String id, String description, BigDecimal amount, LocalDate expenseDate,
                                       LocalTime expenseTime, String category, String subcategory, String merchant,
                                       String paymentMethod, String account, String notes, boolean isRecurring,
                                       String frequency, String reference, String attachmentPath) {
        description = FinanceValidation.requireText(description, "Expense description", 200);
        amount = FinanceValidation.requirePositiveAmount(amount, "Expense amount");
        expenseDate = FinanceValidation.requireDate(expenseDate, "Expense date");
        category = FinanceValidation.requireText(category, "Expense category", 100);
        paymentMethod = FinanceValidation.requireText(paymentMethod, "Payment method", 100);
        notes = FinanceValidation.optionalText(notes, "Expense notes", 500);
        subcategory = subcategory == null ? "" : subcategory.trim();
        merchant = merchant == null ? "" : merchant.trim();
        account = account == null ? "" : account.trim();
        reference = reference == null ? "" : reference.trim();
        attachmentPath = attachmentPath == null ? "" : attachmentPath.trim();
        frequency = frequency == null ? "" : frequency.trim().toUpperCase();
        if (isRecurring && frequency.isEmpty()) throw new IllegalArgumentException("Frequency is required for recurring expenses");
        LocalDate nextOccurrence = isRecurring ? calculateNextOccurrence(expenseDate, frequency) : null;
        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("Description", description); rowData.put("Amount", amount);
        rowData.put("ExpenseDate", expenseDate.toString()); rowData.put("ExpenseTime", expenseTime == null ? "" : expenseTime.toString());
        rowData.put("Category", category); rowData.put("Subcategory", subcategory); rowData.put("Merchant", merchant);
        rowData.put("PaymentMethod", paymentMethod); rowData.put("Account", account); rowData.put("Notes", notes);
        rowData.put("IsRecurring", String.valueOf(isRecurring)); rowData.put("Frequency", frequency);
        rowData.put("NextOccurrence", nextOccurrence == null ? "" : nextOccurrence.toString());
        rowData.put("Reference", reference); rowData.put("AttachmentPath", attachmentPath);
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        storageService.updateRow("Expenses", "ExpenseID", id, rowData);
        return new ExpenseRecord(id, "", description, amount, expenseDate, expenseTime, category, subcategory, merchant,
                paymentMethod, account, notes, isRecurring, frequency, nextOccurrence, null, reference, attachmentPath, now, now);
    }

    public void deleteExpense(Long id) {
        deleteExpense(id.toString());
    }

    public void deleteExpense(String id) {
        storageService.deleteRow("Expenses", "ExpenseID", id);
    }

    public List<ExpenseRecord> getRecurringExpenses(User user) {
        return getUserExpenses(user).stream()
                .filter(record -> record.isRecurring())
                .toList();
    }

    public void processRecurringExpenses(User user) {
        List<ExpenseRecord> recurring = getRecurringExpenses(user);
        LocalDate today = LocalDate.now();

        for (ExpenseRecord expense : recurring) {
            if (expense.getNextOccurrence() != null && !expense.getNextOccurrence().isAfter(today)) {
                LocalDate newDate = calculateNextOccurrence(expense.getExpenseDate(), expense.getFrequency());
                LocalDate newLastOccurrence = expense.getExpenseDate();

                LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
                rowData.put("ExpenseDate", newDate.toString());
                rowData.put("LastOccurrence", newLastOccurrence.toString());
                rowData.put("NextOccurrence", calculateNextOccurrence(newDate, expense.getFrequency()).toString());
                rowData.put("UpdatedAt", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                storageService.updateRow("Expenses", "ExpenseID", expense.getExpenseId(), rowData);
            }
        }
    }

    private LocalDate calculateNextOccurrence(LocalDate baseDate, String frequency) {
        return switch (frequency.toUpperCase()) {
            case "DAILY" -> baseDate.plusDays(1);
            case "WEEKLY" -> baseDate.plusWeeks(1);
            case "MONTHLY" -> baseDate.plusMonths(1);
            case "QUARTERLY" -> baseDate.plusMonths(3);
            case "YEARLY" -> baseDate.plusYears(1);
            default -> baseDate;
        };
    }

    private ExpenseRecord mapRowToExpenseRecord(Row row) {
        return new ExpenseRecord(
                getCellString(row, "ExpenseID"),
                getCellString(row, "UserID"),
                getCellString(row, "Description"),
                parseBigDecimal(row, "Amount"),
                parseLocalDate(row, "ExpenseDate"),
                parseLocalTime(row, "ExpenseTime"),
                getCellString(row, "Category"),
                getCellString(row, "Subcategory"),
                getCellString(row, "Merchant"),
                getCellString(row, "PaymentMethod"),
                getCellString(row, "Account"),
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

    private LocalTime parseLocalTime(Row row, String columnName) {
        String val = getCellString(row, columnName);
        if (val.isEmpty()) return null;
        try {
            return LocalTime.parse(val);
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

    public static class ExpenseRecord {
        private final String expenseId;
        private final String userId;
        private final String description;
        private final BigDecimal amount;
        private final LocalDate expenseDate;
        private final LocalTime expenseTime;
        private final String category;
        private final String subcategory;
        private final String merchant;
        private final String paymentMethod;
        private final String account;
        private final String notes;
        private final boolean isRecurring;
        private final String frequency;
        private final LocalDate nextOccurrence;
        private final LocalDate lastOccurrence;
        private final String reference;
        private final String attachmentPath;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public ExpenseRecord(String expenseId, String userId, String description, BigDecimal amount,
                             LocalDate expenseDate, LocalTime expenseTime, String category, String subcategory,
                             String merchant, String paymentMethod, String account, String notes,
                             boolean isRecurring, String frequency, LocalDate nextOccurrence,
                             LocalDate lastOccurrence, String reference, String attachmentPath,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.expenseId = expenseId;
            this.userId = userId;
            this.description = description;
            this.amount = amount;
            this.expenseDate = expenseDate;
            this.expenseTime = expenseTime;
            this.category = category;
            this.subcategory = subcategory;
            this.merchant = merchant;
            this.paymentMethod = paymentMethod;
            this.account = account;
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

        public String getExpenseId() { return expenseId; }
        public String getUserId() { return userId; }
        public String getDescription() { return description; }
        public BigDecimal getAmount() { return amount; }
        public LocalDate getExpenseDate() { return expenseDate; }
        public LocalTime getExpenseTime() { return expenseTime; }
        public String getCategory() { return category; }
        public String getSubcategory() { return subcategory; }
        public String getMerchant() { return merchant; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getAccount() { return account; }
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