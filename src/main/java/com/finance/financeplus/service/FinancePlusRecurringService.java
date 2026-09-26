package com.finance.financeplus.service;

import com.finance.financeplus.model.FinancePlusRecurring;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.model.entity.User;
import com.finance.service.ExpenseService;
import com.finance.service.IncomeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class FinancePlusRecurringService {

    private final FinancePlusRecurringRepository recurringRepository;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;

    public FinancePlusRecurringService(FinancePlusRecurringRepository recurringRepository,
                                        ExpenseService expenseService,
                                        IncomeService incomeService) {
        this.recurringRepository = recurringRepository;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
    }

    public List<FinancePlusRecurring> getRules(User user) {
        return recurringRepository.findByUserOrderByNextDueDateAsc(user);
    }

    public List<FinancePlusRecurring> getActiveRules(User user) {
        return recurringRepository.findByUserAndActiveTrueOrderByNextDueDateAsc(user);
    }

    public FinancePlusRecurring create(User user, String transactionType, String description,
                                        BigDecimal amount, String category, String paymentMethod,
                                        String frequency, LocalDate nextDueDate, boolean autoPost) {
        FinancePlusRecurring rule = new FinancePlusRecurring();
        rule.setUser(user);
        apply(rule, transactionType, description, amount, category, paymentMethod, frequency, nextDueDate, autoPost);
        return recurringRepository.save(rule);
    }

    public FinancePlusRecurring update(User user, Long id, String transactionType, String description,
                                        BigDecimal amount, String category, String paymentMethod,
                                        String frequency, LocalDate nextDueDate, boolean active, boolean autoPost) {
        FinancePlusRecurring rule = recurringRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Recurring rule not found"));
        apply(rule, transactionType, description, amount, category, paymentMethod, frequency, nextDueDate, autoPost);
        rule.setActive(active);
        return recurringRepository.save(rule);
    }

    public void delete(User user, Long id) {
        FinancePlusRecurring rule = recurringRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Recurring rule not found"));
        recurringRepository.delete(rule);
    }

    public synchronized int processDue(LocalDate throughDate) {
        LocalDate limitDate = throughDate == null ? LocalDate.now() : throughDate;
        int created = 0;
        for (FinancePlusRecurring rule : recurringRepository.findByActiveTrueAndAutoPostTrueAndNextDueDateLessThanEqual(limitDate)) {
            int guard = 0;
            while (!rule.getNextDueDate().isAfter(limitDate) && guard++ < 240) {
                LocalDate occurrenceDate = rule.getNextDueDate();
                if ("INCOME".equals(rule.getTransactionType())) {
                    incomeService.addIncome(rule.getUser(), rule.getDescription(), rule.getAmount(), occurrenceDate,
                            rule.getCategory(), "Finance Plus recurring", "Created by Finance Plus");
                } else {
                    expenseService.addExpense(rule.getUser(), rule.getDescription(), rule.getAmount(), occurrenceDate,
                            rule.getCategory(), rule.getPaymentMethod(), "Created by Finance Plus", null);
                }
                rule.setNextDueDate(nextOccurrence(occurrenceDate, rule.getFrequency()));
                created++;
            }
            recurringRepository.save(rule);
        }
        return created;
    }

    public LocalDate nextOccurrence(LocalDate date, String frequency) {
        return switch (frequency) {
            case "DAILY" -> date.plusDays(1);
            case "WEEKLY" -> date.plusWeeks(1);
            case "QUARTERLY" -> date.plusMonths(3);
            case "YEARLY" -> date.plusYears(1);
            default -> date.plusMonths(1);
        };
    }

    private void apply(FinancePlusRecurring rule, String transactionType, String description,
                       BigDecimal amount, String category, String paymentMethod, String frequency,
                       LocalDate nextDueDate, boolean autoPost) {
        String type = normalizeType(transactionType);
        String normalizedFrequency = normalizeFrequency(frequency);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
        if (nextDueDate == null) {
            throw new IllegalArgumentException("Next due date is required");
        }
        rule.setTransactionType(type);
        rule.setDescription(description.trim());
        rule.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        rule.setCategory(category.trim());
        rule.setPaymentMethod(paymentMethod == null ? "Other" : paymentMethod.trim());
        rule.setFrequency(normalizedFrequency);
        rule.setNextDueDate(nextDueDate);
        rule.setAutoPost(autoPost);
    }

    private String normalizeType(String value) {
        String normalized = value == null ? "EXPENSE" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("INCOME") && !normalized.equals("EXPENSE")) {
            throw new IllegalArgumentException("Transaction type must be INCOME or EXPENSE");
        }
        return normalized;
    }

    private String normalizeFrequency(String value) {
        String normalized = value == null ? "MONTHLY" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported recurrence frequency");
        }
        return normalized;
    }
}
