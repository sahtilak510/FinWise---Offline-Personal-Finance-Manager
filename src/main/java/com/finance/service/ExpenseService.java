package com.finance.service;

import com.finance.model.entity.Expense;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.util.FinanceValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class ExpenseService {
    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public Expense addExpense(User user, String description, BigDecimal amount, LocalDate expenseDate,
                              String category, String paymentMethod, String notes, byte[] receiptImage) {
        return addExpense(user, description, amount, expenseDate, category, paymentMethod, notes,
                receiptImage, false, null);
    }

    public Expense addExpense(User user, String description, BigDecimal amount, LocalDate expenseDate,
                              String category, String paymentMethod, String notes, byte[] receiptImage,
                              boolean recurring, String recurrenceFrequency) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        String frequency = normalizeFrequency(recurring, recurrenceFrequency);
        validateReceipt(receiptImage);

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setDescription(FinanceValidation.requireText(description, "Expense description", 200));
        expense.setAmount(FinanceValidation.requirePositiveAmount(amount, "Expense amount"));
        expense.setExpenseDate(FinanceValidation.requireDate(expenseDate, "Expense date"));
        expense.setCategory(FinanceValidation.requireText(category, "Expense category", 100));
        expense.setPaymentMethod(FinanceValidation.requireText(paymentMethod, "Payment method", 100));
        expense.setNotes(FinanceValidation.optionalText(notes, "Expense notes", 500));
        expense.setReceiptImage(receiptImage);
        applyRecurrence(expense, recurring, frequency, expenseDate);

        return expenseRepository.save(expense);
    }

    public List<Expense> getUserExpenses(User user) {
        return expenseRepository.findByUserOrderByExpenseDateDesc(user);
    }

    public List<Expense> searchExpenses(User user, String query, String category, LocalDate startDate,
                                        LocalDate endDate, BigDecimal minimumAmount, BigDecimal maximumAmount,
                                        Boolean recurring) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return getUserExpenses(user).stream()
                .filter(expense -> search.isBlank()
                        || contains(expense.getDescription(), search)
                        || contains(expense.getCategory(), search)
                        || contains(expense.getPaymentMethod(), search)
                        || contains(expense.getNotes(), search))
                .filter(expense -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(expense.getCategory()))
                .filter(expense -> startDate == null || !expense.getExpenseDate().isBefore(startDate))
                .filter(expense -> endDate == null || !expense.getExpenseDate().isAfter(endDate))
                .filter(expense -> minimumAmount == null || expense.getAmount().compareTo(minimumAmount) >= 0)
                .filter(expense -> maximumAmount == null || expense.getAmount().compareTo(maximumAmount) <= 0)
                .filter(expense -> recurring == null || expense.isRecurring() == recurring)
                .toList();
    }

    public List<String> getUserCategories(User user) {
        return getUserExpenses(user).stream()
                .map(Expense::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    public List<Expense> getExpensesByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return expenseRepository.findByUserAndExpenseDateBetween(user, startDate, endDate);
    }

    public List<Expense> getExpensesByCategory(User user, String category) {
        return expenseRepository.findByUserAndCategory(user, category);
    }

    public BigDecimal getTotalExpensesByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = expenseRepository.getTotalExpensesByDateRange(user, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getTotalExpensesByCategory(User user, String category) {
        BigDecimal total = expenseRepository.getTotalExpensesByCategory(user, category);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Optional<Expense> getExpenseById(Long id) {
        return expenseRepository.findById(id);
    }

    public Optional<Expense> getExpenseById(Long id, User user) {
        return expenseRepository.findByIdAndUser(id, user);
    }

    public Expense saveExpense(Expense expense) {
        return expenseRepository.save(expense);
    }

    public Expense updateExpense(User user, Long id, String description, BigDecimal amount, LocalDate expenseDate,
                                 String category, String paymentMethod, String notes) {
        return updateExpense(user, id, description, amount, expenseDate, category, paymentMethod, notes,
                false, null);
    }

    public Expense updateExpense(User user, Long id, String description, BigDecimal amount, LocalDate expenseDate,
                                 String category, String paymentMethod, String notes,
                                 boolean recurring, String recurrenceFrequency) {
        Expense expense = expenseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        String frequency = normalizeFrequency(recurring, recurrenceFrequency);

        expense.setDescription(FinanceValidation.requireText(description, "Expense description", 200));
        expense.setAmount(FinanceValidation.requirePositiveAmount(amount, "Expense amount"));
        expense.setExpenseDate(FinanceValidation.requireDate(expenseDate, "Expense date"));
        expense.setCategory(FinanceValidation.requireText(category, "Expense category", 100));
        expense.setPaymentMethod(FinanceValidation.requireText(paymentMethod, "Payment method", 100));
        expense.setNotes(FinanceValidation.optionalText(notes, "Expense notes", 500));
        applyRecurrence(expense, recurring, frequency, expenseDate);

        return expenseRepository.save(expense);
    }

    public void deleteExpense(User user, Long id) {
        Expense expense = expenseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        expenseRepository.delete(expense);
    }

    public int processDueRecurringTransactions(User user, LocalDate throughDate) {
        LocalDate limitDate = throughDate == null ? LocalDate.now() : throughDate;
        int created = 0;
        for (Expense template : expenseRepository.findByUserOrderByExpenseDateDesc(user)) {
            if (!template.isRecurring() || template.getNextOccurrence() == null) {
                continue;
            }
            int guard = 0;
            while (!template.getNextOccurrence().isAfter(limitDate) && guard++ < 240) {
                LocalDate occurrenceDate = template.getNextOccurrence();
                Expense occurrence = new Expense();
                occurrence.setUser(user);
                occurrence.setDescription(template.getDescription());
                occurrence.setAmount(template.getAmount());
                occurrence.setExpenseDate(occurrenceDate);
                occurrence.setCategory(template.getCategory());
                occurrence.setPaymentMethod(template.getPaymentMethod());
                occurrence.setNotes(template.getNotes());
                occurrence.setReceiptImage(template.getReceiptImage());
                expenseRepository.save(occurrence);
                template.setNextOccurrence(calculateNextOccurrence(occurrenceDate,
                        template.getRecurrenceFrequency()));
                created++;
            }
            expenseRepository.save(template);
        }
        return created;
    }

    public LocalDate calculateNextOccurrence(LocalDate date, String frequency) {
        return switch (frequency) {
            case "DAILY" -> date.plusDays(1);
            case "WEEKLY" -> date.plusWeeks(1);
            case "QUARTERLY" -> date.plusMonths(3);
            case "YEARLY" -> date.plusYears(1);
            default -> date.plusMonths(1);
        };
    }

    private void applyRecurrence(Expense expense, boolean recurring, String frequency, LocalDate startDate) {
        expense.setRecurring(recurring);
        expense.setRecurrenceFrequency(recurring ? frequency : null);
        expense.setNextOccurrence(recurring ? calculateNextOccurrence(startDate, frequency) : null);
    }

    private String normalizeFrequency(boolean recurring, String frequency) {
        if (!recurring) {
            return null;
        }
        String normalized = frequency == null ? "" : frequency.trim().toUpperCase();
        if (!List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY").contains(normalized)) {
            throw new IllegalArgumentException("Frequency must be DAILY, WEEKLY, MONTHLY, QUARTERLY or YEARLY");
        }
        return normalized;
    }

    private void validateReceipt(byte[] receiptImage) {
        if (receiptImage != null && receiptImage.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Receipt image must be 5 MB or smaller");
        }
    }
}
