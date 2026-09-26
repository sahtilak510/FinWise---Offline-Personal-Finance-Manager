package com.finance.service.excel;

import com.finance.model.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SmartInsightsService {

    private final IncomeExcelService incomeExcelService;
    private final ExpenseExcelService expenseExcelService;
    private final BudgetExcelService budgetExcelService;
    private final GoalExcelService goalExcelService;

    public SmartInsightsService(IncomeExcelService incomeExcelService,
                                ExpenseExcelService expenseExcelService,
                                BudgetExcelService budgetExcelService,
                                GoalExcelService goalExcelService) {
        this.incomeExcelService = incomeExcelService;
        this.expenseExcelService = expenseExcelService;
        this.budgetExcelService = budgetExcelService;
        this.goalExcelService = goalExcelService;
    }

    public List<Insight> generateInsights(User user) {
        List<Insight> insights = new ArrayList<>();

        insights.addAll(generateSpendingInsights(user));
        insights.addAll(generateBudgetInsights(user));
        insights.addAll(generateIncomeInsights(user));
        insights.addAll(generateGoalInsights(user));
        insights.addAll(generateRecurringInsights(user));
        insights.addAll(generateSavingsInsights(user));

        return insights.stream()
                .sorted((left, right) -> right.getPriority().compareTo(left.getPriority()))
                .limit(10)
                .toList();
    }

    private List<Insight> generateSpendingInsights(User user) {
        List<Insight> insights = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);

        LocalDate currentStart = currentMonth.atDay(1);
        LocalDate currentEnd = currentMonth.atEndOfMonth();
        LocalDate lastStart = lastMonth.atDay(1);
        LocalDate lastEnd = lastMonth.atEndOfMonth();

        List<ExpenseExcelService.ExpenseRecord> currentExpenses = expenseExcelService.getExpensesByDateRange(user, currentStart, currentEnd);
        List<ExpenseExcelService.ExpenseRecord> lastExpenses = expenseExcelService.getExpensesByDateRange(user, lastStart, lastEnd);

        BigDecimal currentTotal = currentExpenses.stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        BigDecimal lastTotal = lastExpenses.stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, (left, right) -> left.add(right));

        if (currentTotal.compareTo(BigDecimal.ZERO) > 0 && lastTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal change = currentTotal.subtract(lastTotal).divide(lastTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            double changePct = change.doubleValue();

            if (changePct > 20) {
                insights.add(new Insight("HIGH", "spending_increase",
                        String.format("Your spending increased %.1f%% compared to last month ($%s vs $%s)",
                                changePct, currentTotal.setScale(2), lastTotal.setScale(2)),
                        "Consider reviewing your recent expenses to identify areas to cut back."));
            } else if (changePct < -20) {
                insights.add(new Insight("MEDIUM", "spending_decrease",
                        String.format("Your spending decreased %.1f%% compared to last month. Great job!", Math.abs(changePct)),
                        "Keep up the good work!"));
            }
        }

        Map<String, BigDecimal> categorySpending = currentExpenses.stream()
                .collect(Collectors.groupingBy(expense -> expense.getCategory(),
                        Collectors.reducing(BigDecimal.ZERO, expense -> expense.getAmount(), (left, right) -> left.add(right))));

        if (!categorySpending.isEmpty()) {
            Map.Entry<String, BigDecimal> topCategory = categorySpending.entrySet().stream()
                    .max((left, right) -> left.getValue().compareTo(right.getValue()))
                    .orElse(null);

            if (topCategory != null) {
                BigDecimal catTotal = topCategory.getValue();
                BigDecimal pct = currentTotal.compareTo(BigDecimal.ZERO) > 0
                        ? catTotal.divide(currentTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;

                insights.add(new Insight("MEDIUM", "top_category",
                        String.format("Your largest expense category is %s at $%s (%.1f%% of total spending)",
                                topCategory.getKey(), catTotal.setScale(2), pct.doubleValue()),
                        pct.doubleValue() > 40 ? "This category represents a large portion of your spending. Consider setting a budget for it." : "Monitor this category to ensure it stays within reasonable limits."));
            }
        }

        Map<String, BigDecimal> merchantSpending = currentExpenses.stream()
                .filter(e -> e.getMerchant() != null && !e.getMerchant().isBlank())
                .collect(Collectors.groupingBy(expense -> expense.getMerchant(),
                        Collectors.reducing(BigDecimal.ZERO, expense -> expense.getAmount(), (left, right) -> left.add(right))));

        if (!merchantSpending.isEmpty()) {
            Map.Entry<String, BigDecimal> topMerchant = merchantSpending.entrySet().stream()
                    .max((left, right) -> left.getValue().compareTo(right.getValue()))
                    .orElse(null);

            if (topMerchant != null) {
                insights.add(new Insight("LOW", "top_merchant",
                        String.format("You spent the most at %s ($%s this month)", topMerchant.getKey(), topMerchant.getValue().setScale(2)),
                        "Consider if this is a necessary expense or if there are alternatives."));
            }
        }

        if (currentExpenses.isEmpty()) {
            insights.add(new Insight("LOW", "no_spending",
                    "No expenses recorded this month yet.",
                    "Start tracking your expenses to get personalized insights."));
        }

        return insights;
    }

    private List<Insight> generateBudgetInsights(User user) {
        List<Insight> insights = new ArrayList<>();
        List<BudgetExcelService.BudgetRecord> budgets = budgetExcelService.getUserActiveBudgets(user);

        for (BudgetExcelService.BudgetRecord budget : budgets) {
            double pct = budget.getPercentageUsed();
            if (pct >= 100) {
                insights.add(new Insight("CRITICAL", "budget_over",
                        String.format("Budget for %s is OVER ($%s spent of $%s limit)",
                                budget.getCategory(), budget.getSpentAmount().setScale(2), budget.getLimitAmount().setScale(2)),
                        "Immediate action needed: reduce spending in this category or increase the budget limit."));
            } else if (pct >= 90) {
                insights.add(new Insight("HIGH", "budget_critical",
                        String.format("Budget for %s is at %.0f%% ($%s of $%s)",
                                budget.getCategory(), pct, budget.getSpentAmount().setScale(2), budget.getLimitAmount().setScale(2)),
                        "You're close to exceeding this budget. Review upcoming expenses."));
            } else if (pct >= 75) {
                insights.add(new Insight("MEDIUM", "budget_warning",
                        String.format("Budget for %s is at %.0f%% ($%s of $%s)",
                                budget.getCategory(), pct, budget.getSpentAmount().setScale(2), budget.getLimitAmount().setScale(2)),
                        "Approaching budget limit. Plan remaining spending carefully."));
            }
        }

        if (budgets.isEmpty()) {
            insights.add(new Insight("LOW", "no_budgets",
                    "No active budgets set up.",
                    "Create budgets to track spending limits and get alerts when you're close to overspending."));
        }

        return insights;
    }

    private List<Insight> generateIncomeInsights(User user) {
        List<Insight> insights = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);

        LocalDate currentStart = currentMonth.atDay(1);
        LocalDate currentEnd = currentMonth.atEndOfMonth();
        LocalDate lastStart = lastMonth.atDay(1);
        LocalDate lastEnd = lastMonth.atEndOfMonth();

        BigDecimal currentIncome = incomeExcelService.getTotalIncomeByDateRange(user, currentStart, currentEnd);
        BigDecimal lastIncome = incomeExcelService.getTotalIncomeByDateRange(user, lastStart, lastEnd);

        if (currentIncome.compareTo(BigDecimal.ZERO) > 0 && lastIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal change = currentIncome.subtract(lastIncome).divide(lastIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            double changePct = change.doubleValue();

            if (changePct > 15) {
                insights.add(new Insight("MEDIUM", "income_increase",
                        String.format("Your income increased %.1f%% compared to last month", changePct),
                        "Consider allocating extra income to savings or debt repayment."));
            } else if (changePct < -15) {
                insights.add(new Insight("HIGH", "income_decrease",
                        String.format("Your income decreased %.1f%% compared to last month", Math.abs(changePct)),
                        "Review your income sources and consider adjusting your budget accordingly."));
            }
        }

        BigDecimal monthlyExpenses = expenseExcelService.getTotalExpensesByDateRange(user, currentStart, currentEnd);
        if (currentIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savings = currentIncome.subtract(monthlyExpenses);
            BigDecimal savingsRate = savings.divide(currentIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            if (savingsRate.doubleValue() >= 20) {
                insights.add(new Insight("LOW", "good_savings_rate",
                        String.format("Your savings rate is %.1f%% this month. Excellent!", savingsRate.doubleValue()),
                        "Consider investing surplus savings for long-term growth."));
            } else if (savingsRate.doubleValue() < 0) {
                insights.add(new Insight("CRITICAL", "negative_savings",
                        String.format("You're spending more than you earn this month (deficit: $%s)", savings.abs().setScale(2)),
                        "Urgent: Review expenses and find ways to reduce spending or increase income."));
            } else if (savingsRate.doubleValue() < 10) {
                insights.add(new Insight("MEDIUM", "low_savings_rate",
                        String.format("Your savings rate is %.1f%% this month", savingsRate.doubleValue()),
                        "Aim for at least 20% savings rate. Look for expenses to reduce."));
            }
        }

        return insights;
    }

    private List<Insight> generateGoalInsights(User user) {
        List<Insight> insights = new ArrayList<>();
        List<GoalExcelService.GoalRecord> goals = goalExcelService.getUserGoals(user);

        for (GoalExcelService.GoalRecord goal : goals) {
            if (!goal.isActive()) continue;

            if (goal.isOverdue() && !goal.isCompleted()) {
                insights.add(new Insight("HIGH", "goal_overdue",
                        String.format("Goal \"%s\" is overdue (target date: %s, progress: %.1f%%)",
                                goal.getGoalName(), goal.getTargetDate(), goal.getProgressPercentage()),
                        "Consider extending the deadline or increasing contributions to catch up."));
            } else if (!goal.isCompleted()) {
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate());
                if (daysLeft <= 30 && goal.getProgressPercentage() < 50) {
                    insights.add(new Insight("MEDIUM", "goal_deadline_approaching",
                            String.format("Goal \"%s\" deadline in %d days (progress: %.1f%%)",
                                    goal.getGoalName(), daysLeft, goal.getProgressPercentage()),
                            "You may need to increase contributions to meet this goal on time."));
                }
            }
        }

        int activeGoals = (int) goals.stream().filter(goal -> goal.isActive()).count();
        if (activeGoals == 0) {
            insights.add(new Insight("LOW", "no_goals",
                    "No active financial goals set.",
                    "Create goals to stay motivated and track progress toward your financial objectives."));
        }

        return insights;
    }

    private List<Insight> generateRecurringInsights(User user) {
        List<Insight> insights = new ArrayList<>();
        List<ExpenseExcelService.ExpenseRecord> recurring = expenseExcelService.getRecurringExpenses(user);

        if (!recurring.isEmpty()) {
            BigDecimal monthlyRecurring = recurring.stream()
                    .map(expense -> expense.getAmount())
                    .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));

            YearMonth currentMonth = YearMonth.now();
            LocalDate start = currentMonth.atDay(1);
            LocalDate end = currentMonth.atEndOfMonth();
            BigDecimal monthlyIncome = incomeExcelService.getTotalIncomeByDateRange(user, start, end);

            if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = monthlyRecurring.divide(monthlyIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                double ratioPct = ratio.doubleValue();

                if (ratioPct > 50) {
                    insights.add(new Insight("HIGH", "high_recurring_burden",
                            String.format("Recurring expenses represent %.1f%% of your monthly income ($%s of $%s)",
                                    ratioPct, monthlyRecurring.setScale(2), monthlyIncome.setScale(2)),
                                    "High recurring costs limit your financial flexibility. Review subscriptions and regular payments."));
                } else if (ratioPct > 30) {
                    insights.add(new Insight("MEDIUM", "moderate_recurring_burden",
                            String.format("Recurring expenses are %.1f%% of your monthly income", ratioPct),
                            "Monitor recurring payments to ensure they're all necessary."));
                }
            }

            LocalDate today = LocalDate.now();
            List<ExpenseExcelService.ExpenseRecord> upcoming = recurring.stream()
                    .filter(e -> e.getNextOccurrence() != null && !e.getNextOccurrence().isAfter(today.plusDays(7)))
                    .toList();

            if (!upcoming.isEmpty()) {
                String merchants = upcoming.stream()
                        .map(e -> e.getMerchant() + " ($" + e.getAmount() + ")")
                        .collect(Collectors.joining(", "));
                insights.add(new Insight("MEDIUM", "upcoming_recurring",
                        String.format("Upcoming recurring payments this week: %s", merchants),
                        "Ensure sufficient funds are available for these payments."));
            }
        }

        return insights;
    }

    private List<Insight> generateSavingsInsights(User user) {
        List<Insight> insights = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();

        BigDecimal monthlyIncome = incomeExcelService.getTotalIncomeByDateRange(user, start, end);
        BigDecimal monthlyExpenses = expenseExcelService.getTotalExpensesByDateRange(user, start, end);

        if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savings = monthlyIncome.subtract(monthlyExpenses);
            BigDecimal savingsRate = savings.divide(monthlyIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            if (savingsRate.doubleValue() >= 30) {
                insights.add(new Insight("LOW", "excellent_savings",
                        String.format("Outstanding! You're saving %.1f%% of your income this month", savingsRate.doubleValue()),
                        "Consider investing your surplus for long-term wealth building."));
            }
        }

        return insights;
    }

    public static class Insight {
        private final String priority;
        private final String type;
        private final String message;
        private final String recommendation;

        public Insight(String priority, String type, String message, String recommendation) {
            this.priority = priority;
            this.type = type;
            this.message = message;
            this.recommendation = recommendation;
        }

        public String getPriority() { return priority; }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public String getRecommendation() { return recommendation; }

        public int getPriorityValue() {
            return switch (priority) {
                case "CRITICAL" -> 4;
                case "HIGH" -> 3;
                case "MEDIUM" -> 2;
                default -> 1;
            };
        }
    }
}