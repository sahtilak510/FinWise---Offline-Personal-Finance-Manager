package com.finance.habits;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import java.util.List;

/**
 * ABSTRACTION — every student-money rule implements this contract.
 * HabitService talks only to this interface (POLYMORPHISM),
 * so new rules can be added without changing existing code (Open/Closed Principle).
 */
public interface SpendingRule {
    String getId();
    String getTitle();
    RuleResult evaluate(List<Expense> expenses, List<Income> incomes);
}
