package com.finance.habits;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Rule 2 — ORIGINAL: Hostel Mess vs Outside Food detector.
 * No other student project has this. Flags Zomato/Swiggy/canteen overspend,
 * the #1 hostel money leak.
 */
@Component
public class HostelFoodRule extends AbstractSpendingRule {

    private static final String[] OUTSIDE_KEYWORDS = {
            "zomato", "swiggy", "restaurant", "cafe", "pizza", "burger",
            "canteen", "dhaba", "dominos", "kfc", "outside", "party", "treat"
    };

    @Override
    public String getId() { return "hostel-food"; }

    @Override
    public String getTitle() { return "Mess vs Outside Food"; }

    @Override
    public RuleResult evaluate(List<Expense> expenses, List<Income> incomes) {
        BigDecimal spent = totalExpenses(expenses);
        if (spent.compareTo(BigDecimal.ZERO) <= 0) {
            return new RuleResult(getId(), getTitle(), 50, "OK",
                    "No expenses yet — outside-food check needs data.",
                    "Tip: mess is already paid, every outside order is extra.");
        }

        BigDecimal foodTotal = sumByCategory(expenses, "Food");
        BigDecimal outsideTotal = BigDecimal.ZERO;
        if (expenses != null) {
            for (Expense e : expenses) {
                if (e == null || e.getAmount() == null) continue;
                String hay = ((e.getDescription() == null ? "" : e.getDescription()) + " "
                        + (e.getNotes() == null ? "" : e.getNotes())).toLowerCase();
                for (String k : OUTSIDE_KEYWORDS) {
                    if (hay.contains(k)) { outsideTotal = outsideTotal.add(e.getAmount()); break; }
                }
            }
        }

        BigDecimal foodPct = spent.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : foodTotal.multiply(BigDecimal.valueOf(100)).divide(spent, 1, RoundingMode.HALF_UP);

        int score;
        String status;
        if (outsideTotal.compareTo(BigDecimal.ZERO) == 0 && foodPct.doubleValue() <= 35) {
            score = 95; status = "GOOD";
        } else if (foodPct.doubleValue() <= 35) {
            score = 75; status = "GOOD";
        } else if (foodPct.doubleValue() <= 50) {
            score = 55; status = "OK";
        } else {
            score = 25; status = "BAD";
        }

        String msg = String.format("Food is %s%% of spending, outside-food orders total ₹%s.",
                foodPct, outsideTotal.setScale(0, RoundingMode.HALF_UP));
        String tip = "BAD".equals(status)
                ? "Tip: cap outside food to 2x/week — mess + tiffin covers the rest."
                : "Tip: great control — keep one fixed 'treat day' per week.";

        return new RuleResult(getId(), getTitle(), score, status, msg, tip);
    }
}
