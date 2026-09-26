package com.finance.habits;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Rule 1 — Student 50/30/20: Needs 50%, Wants 30%, Savings 20%.
 * Needs = Food, Rent, Bills, Utilities, Healthcare, Education, Transportation.
 * Wants = Shopping, Entertainment, Travel, Personal Care.
 */
@Component
public class NeedWantRule extends AbstractSpendingRule {

    @Override
    public String getId() { return "need-want"; }

    @Override
    public String getTitle() { return "Student 50/30/20 Balance"; }

    @Override
    public RuleResult evaluate(List<Expense> expenses, List<Income> incomes) {
        BigDecimal income = totalIncome(incomes);
        BigDecimal spent = totalExpenses(expenses);

        if (income.compareTo(BigDecimal.ZERO) <= 0 || spent.compareTo(BigDecimal.ZERO) <= 0) {
            return new RuleResult(getId(), getTitle(), 50, "OK",
                    "Add income and expenses to check your 50/30/20 balance.",
                    "Tip: pocket money counts as income — add it under Income.");
        }

        BigDecimal needs = sumByCategory(expenses, "Food", "Rent", "Bills", "Utilities",
                "Healthcare", "Education", "Transportation");
        BigDecimal wants = sumByCategory(expenses, "Shopping", "Entertainment", "Travel", "Personal Care");

        BigDecimal needsPct = needs.multiply(BigDecimal.valueOf(100))
                .divide(spent, 1, RoundingMode.HALF_UP);
        BigDecimal wantsPct = wants.multiply(BigDecimal.valueOf(100))
                .divide(spent, 1, RoundingMode.HALF_UP);
        BigDecimal savedPct = income.subtract(spent).multiply(BigDecimal.valueOf(100))
                .divide(income, 1, RoundingMode.HALF_UP);

        // Score: penalise distance from 50/30/20 ideal
        double drift = Math.abs(needsPct.doubleValue() - 50)
                + Math.abs(wantsPct.doubleValue() - 30)
                + Math.abs(Math.max(savedPct.doubleValue(), 0) - 20);
        int score = (int) Math.max(5, 100 - drift * 1.5);

        String msg = String.format("Needs %s%%, Wants %s%%, Saved %s%% of income. Ideal is 50/30/20.",
                needsPct, wantsPct, savedPct.max(BigDecimal.ZERO));
        String tip = wantsPct.doubleValue() > 35
                ? "Tip: Wants cross 35% — cut one outing per week to recover savings."
                : "Tip: keep needs under 55% and auto-move 10% to your goal first.";

        return new RuleResult(getId(), getTitle(), score, statusFor(score), msg, tip);
    }
}
