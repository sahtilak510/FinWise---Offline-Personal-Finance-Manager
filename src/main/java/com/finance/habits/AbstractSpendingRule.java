package com.finance.habits;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import java.math.BigDecimal;
import java.util.List;

/**
 * INHERITANCE base — shared helpers for all rules.
 * Subclasses inherit these instead of duplicating null-safe math.
 */
public abstract class AbstractSpendingRule implements SpendingRule {

    protected BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    protected BigDecimal totalExpenses(List<Expense> expenses) {
        if (expenses == null) return BigDecimal.ZERO;
        return expenses.stream()
                .filter(e -> e != null && e.getAmount() != null)
                .map(e -> safeAmount(e.getAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> safeAmount(left).add(safeAmount(right)));
    }

    protected BigDecimal totalIncome(List<Income> incomes) {
        if (incomes == null) return BigDecimal.ZERO;
        return incomes.stream()
                .filter(i -> i != null && i.getAmount() != null)
                .map(i -> safeAmount(i.getAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> safeAmount(left).add(safeAmount(right)));
    }

    protected BigDecimal sumByCategory(List<Expense> expenses, String... categories) {
        if (expenses == null || categories == null) return BigDecimal.ZERO;
        return expenses.stream()
                .filter(e -> e != null && e.getAmount() != null && e.getCategory() != null)
                .filter(e -> {
                    for (String c : categories) {
                        if (c != null && c.equalsIgnoreCase(e.getCategory().trim())) return true;
                    }
                    return false;
                })
                .map(e -> safeAmount(e.getAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> safeAmount(left).add(safeAmount(right)));
    }

    protected String statusFor(int score) {
        if (score >= 75) return "GOOD";
        if (score >= 45) return "OK";
        return "BAD";
    }
}
