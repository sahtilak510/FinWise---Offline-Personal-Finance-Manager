package com.finance.habits;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.service.ExpenseService;
import com.finance.service.IncomeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * POLYMORPHISM in action — holds List&lt;SpendingRule&gt;.
 * Spring injects all 3 rule beans; this class never knows their concrete types.
 * Adding a 4th rule = zero changes here.
 */
@Service
public class HabitService {

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final List<SpendingRule> rules;

    public HabitService(ExpenseService expenseService, IncomeService incomeService, List<SpendingRule> rules) {
        this.expenseService = expenseService;
        this.incomeService = incomeService;
        this.rules = rules;
    }

    public HabitReport buildReport(User user) {
        List<Expense> expenses = user == null ? List.of() : expenseService.getUserExpenses(user);
        List<Income> incomes = user == null ? List.of() : incomeService.getUserIncome(user);

        List<RuleResult> results = new ArrayList<>();
        for (SpendingRule rule : rules) {
            try {
                results.add(rule.evaluate(expenses, incomes));
            } catch (Exception ignored) {
                results.add(new RuleResult(rule.getId(), rule.getTitle(), 50, "OK",
                        "Could not evaluate right now.", "Tip: add more data and retry."));
            }
        }

        BigDecimal income = total(incomes);
        BigDecimal spent = totalExp(expenses);
        BigDecimal savingsRate = income.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                : income.subtract(spent).multiply(BigDecimal.valueOf(100))
                        .divide(income, 1, RoundingMode.HALF_UP);

        int overall = results.isEmpty() ? 0
                : (int) Math.round(results.stream().mapToInt(result -> result.getScore()).average().orElse(0));
        String grade = overall >= 75 ? "A — Disciplined" : overall >= 55 ? "B — Improving" : overall >= 35 ? "C — Leaky" : "D — Reset needed";

        return new HabitReport(overall, grade, results, income, spent, savingsRate, !expenses.isEmpty() || !incomes.isEmpty());
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal total(List<Income> incomes) {
        if (incomes == null) return BigDecimal.ZERO;
        return incomes.stream().filter(i -> i != null && i.getAmount() != null)
                .map(i -> safeAmount(i.getAmount())).reduce(BigDecimal.ZERO, (left, right) -> safeAmount(left).add(safeAmount(right)));
    }

    private BigDecimal totalExp(List<Expense> expenses) {
        if (expenses == null) return BigDecimal.ZERO;
        return expenses.stream().filter(e -> e != null && e.getAmount() != null)
                .map(e -> safeAmount(e.getAmount())).reduce(BigDecimal.ZERO, (left, right) -> safeAmount(left).add(safeAmount(right)));
    }

    /** Report DTO — immutable snapshot for the view. */
    public record HabitReport(int overallScore, String grade, List<RuleResult> results,
                              BigDecimal totalIncome, BigDecimal totalExpenses,
                              BigDecimal savingsRate, boolean hasData) {}
}
