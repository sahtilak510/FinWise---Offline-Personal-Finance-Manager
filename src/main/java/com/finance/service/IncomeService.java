package com.finance.service;

import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.IncomeRepository;
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
public class IncomeService {
    private final IncomeRepository incomeRepository;

    public IncomeService(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    public Income addIncome(User user, String description, BigDecimal amount, LocalDate incomeDate,
                            String category, String incomeSource, String notes) {
        return addIncome(user, description, amount, incomeDate, category, incomeSource, notes,
                false, null);
    }

    public Income addIncome(User user, String description, BigDecimal amount, LocalDate incomeDate,
                            String category, String incomeSource, String notes,
                            boolean recurring, String recurrenceFrequency) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        String frequency = normalizeFrequency(recurring, recurrenceFrequency);

        Income income = new Income();
        income.setUser(user);
        income.setDescription(FinanceValidation.requireText(description, "Income description", 200));
        income.setAmount(FinanceValidation.requirePositiveAmount(amount, "Income amount"));
        income.setIncomeDate(FinanceValidation.requireDate(incomeDate, "Income date"));
        income.setCategory(FinanceValidation.requireText(category, "Income category", 100));
        income.setIncomeSource(FinanceValidation.requireText(incomeSource, "Income source", 150));
        income.setNotes(FinanceValidation.optionalText(notes, "Income notes", 500));
        applyRecurrence(income, recurring, frequency, incomeDate);

        return incomeRepository.save(income);
    }

    public List<Income> getUserIncome(User user) {
        return incomeRepository.findByUserOrderByIncomeDateDesc(user);
    }

    public List<Income> searchIncome(User user, String query, String category, LocalDate startDate,
                                     LocalDate endDate, BigDecimal minimumAmount, BigDecimal maximumAmount,
                                     Boolean recurring) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return getUserIncome(user).stream()
                .filter(income -> search.isBlank()
                        || contains(income.getDescription(), search)
                        || contains(income.getCategory(), search)
                        || contains(income.getIncomeSource(), search)
                        || contains(income.getNotes(), search))
                .filter(income -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(income.getCategory()))
                .filter(income -> startDate == null || !income.getIncomeDate().isBefore(startDate))
                .filter(income -> endDate == null || !income.getIncomeDate().isAfter(endDate))
                .filter(income -> minimumAmount == null || income.getAmount().compareTo(minimumAmount) >= 0)
                .filter(income -> maximumAmount == null || income.getAmount().compareTo(maximumAmount) <= 0)
                .filter(income -> recurring == null || income.isRecurring() == recurring)
                .toList();
    }

    public List<String> getUserCategories(User user) {
        return getUserIncome(user).stream()
                .map(Income::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    public List<Income> getIncomeByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return incomeRepository.findByUserAndIncomeDateBetween(user, startDate, endDate);
    }

    public List<Income> getIncomeByCategory(User user, String category) {
        return incomeRepository.findByUserAndCategory(user, category);
    }

    public BigDecimal getTotalIncomeByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = incomeRepository.getTotalIncomeByDateRange(user, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getTotalIncomeByCategory(User user, String category) {
        BigDecimal total = incomeRepository.getTotalIncomeByCategory(user, category);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Optional<Income> getIncomeById(Long id) {
        return incomeRepository.findById(id);
    }

    public Optional<Income> getIncomeById(Long id, User user) {
        return incomeRepository.findByIdAndUser(id, user);
    }

    public Income updateIncome(User user, Long id, String description, BigDecimal amount, LocalDate incomeDate,
                               String category, String incomeSource, String notes) {
        return updateIncome(user, id, description, amount, incomeDate, category, incomeSource, notes,
                false, null);
    }

    public Income updateIncome(User user, Long id, String description, BigDecimal amount, LocalDate incomeDate,
                               String category, String incomeSource, String notes,
                               boolean recurring, String recurrenceFrequency) {
        Income income = incomeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Income not found"));
        String frequency = normalizeFrequency(recurring, recurrenceFrequency);

        income.setDescription(FinanceValidation.requireText(description, "Income description", 200));
        income.setAmount(FinanceValidation.requirePositiveAmount(amount, "Income amount"));
        income.setIncomeDate(FinanceValidation.requireDate(incomeDate, "Income date"));
        income.setCategory(FinanceValidation.requireText(category, "Income category", 100));
        income.setIncomeSource(FinanceValidation.requireText(incomeSource, "Income source", 150));
        income.setNotes(FinanceValidation.optionalText(notes, "Income notes", 500));
        applyRecurrence(income, recurring, frequency, incomeDate);

        return incomeRepository.save(income);
    }

    public void deleteIncome(User user, Long id) {
        Income income = incomeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Income not found"));
        incomeRepository.delete(income);
    }

    public int processDueRecurringTransactions(User user, LocalDate throughDate) {
        LocalDate limitDate = throughDate == null ? LocalDate.now() : throughDate;
        int created = 0;
        for (Income template : incomeRepository.findByUserOrderByIncomeDateDesc(user)) {
            if (!template.isRecurring() || template.getNextOccurrence() == null) {
                continue;
            }
            int guard = 0;
            while (!template.getNextOccurrence().isAfter(limitDate) && guard++ < 240) {
                LocalDate occurrenceDate = template.getNextOccurrence();
                Income occurrence = new Income();
                occurrence.setUser(user);
                occurrence.setDescription(template.getDescription());
                occurrence.setAmount(template.getAmount());
                occurrence.setIncomeDate(occurrenceDate);
                occurrence.setCategory(template.getCategory());
                occurrence.setIncomeSource(template.getIncomeSource());
                occurrence.setNotes(template.getNotes());
                incomeRepository.save(occurrence);
                template.setNextOccurrence(calculateNextOccurrence(occurrenceDate,
                        template.getRecurrenceFrequency()));
                created++;
            }
            incomeRepository.save(template);
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

    private void applyRecurrence(Income income, boolean recurring, String frequency, LocalDate startDate) {
        income.setRecurring(recurring);
        income.setRecurrenceFrequency(recurring ? frequency : null);
        income.setNextOccurrence(recurring ? calculateNextOccurrence(startDate, frequency) : null);
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
}
