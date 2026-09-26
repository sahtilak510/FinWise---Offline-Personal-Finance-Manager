package com.finance.habits;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Rule 3 — Gamified No-Spend Streak (last 30 days).
 * Rewards consecutive days with zero expense — hostel students love streaks.
 */
@Component
public class StreakRule extends AbstractSpendingRule {

    @Override
    public String getId() { return "no-spend-streak"; }

    @Override
    public String getTitle() { return "No-Spend Streak"; }

    @Override
    public RuleResult evaluate(List<Expense> expenses, List<Income> incomes) {
        if (expenses == null || expenses.isEmpty()) {
            return new RuleResult(getId(), getTitle(), 50, "OK",
                    "No expenses yet — your first no-spend streak starts today!",
                    "Tip: one no-spend day per week saves ~₹2,000/month.");
        }

        Set<LocalDate> spentDays = new HashSet<>();
        for (Expense e : expenses) {
            if (e != null && e.getExpenseDate() != null) spentDays.add(e.getExpenseDate());
        }

        LocalDate today = LocalDate.now();
        int current = 0;
        LocalDate d = today;
        // count back while day has no expense (ignore future dates)
        while (!spentDays.contains(d)) {
            current++;
            d = d.minusDays(1);
            if (current > 30) break;
        }
        // if today itself has expense, streak is 0 by definition above loop gives 0 — correct.

        int longest = 0, run = 0;
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            if (spentDays.contains(day)) { run = 0; }
            else { run++; longest = Math.max(longest, run); }
        }

        int score = Math.min(100, longest * 15 + current * 5);
        String msg = String.format("Current streak %d day(s), best in 30 days %d day(s).", current, longest);
        String tip = longest >= 4
                ? "Tip: streak master! Protect it — carry water + tiffin on streak days."
                : "Tip: try a 3-day streak challenge next week.";

        return new RuleResult(getId(), getTitle(), Math.max(score, 10), statusFor(score), msg, tip);
    }
}
