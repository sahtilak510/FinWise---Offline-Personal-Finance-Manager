package com.finance.service.excel;

import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.BudgetRepository;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.FinancialGoalRepository;
import com.finance.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinancialHealthScoreService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final FinancialGoalRepository goalRepository;

    public FinancialHealthScoreService(IncomeRepository incomeRepository,
                                       ExpenseRepository expenseRepository,
                                       BudgetRepository budgetRepository,
                                       FinancialGoalRepository goalRepository) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
    }

    public FinancialHealthScore calculateHealthScore(User user) {
        YearMonth month = YearMonth.now();
        BigDecimal income = incomeRepository.findByUserAndIncomeDateBetween(user, month.atDay(1), month.atEndOfMonth())
                .stream().map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = expenseRepository.findByUserAndExpenseDateBetween(user, month.atDay(1), month.atEndOfMonth())
                .stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (income.compareTo(BigDecimal.ZERO) == 0 && expenses.compareTo(BigDecimal.ZERO) == 0) {
            return new FinancialHealthScore(0, "N/A", List.of("Add income or expenses to calculate a health score."),
                    0, 0, 0, 0, 0);
        }

        double savingsScore = scoreSavings(income, expenses, 40);
        double spendingScore = scoreSpending(income, expenses, 20);
        double budgetScore = scoreBudgets(user, 25);
        double goalScore = scoreGoals(user, 15);
        double recurringScore = scoreRecurringBurden(user, income, 15);
        int score = (int) Math.round(savingsScore + spendingScore + budgetScore + goalScore + recurringScore);
        score = Math.max(0, Math.min(100, score));

        List<String> factors = new ArrayList<>();
        factors.add(String.format(java.util.Locale.ROOT, "Savings: %.1f%% (%.0f/40)", percentage(income, income.subtract(expenses)), savingsScore));
        factors.add(String.format(java.util.Locale.ROOT, "Spending control: %.1f%% of income (%.0f/20)", percentage(income, expenses), spendingScore));
        factors.add(String.format(java.util.Locale.ROOT, "Budget adherence: %.0f/25", budgetScore));
        factors.add(String.format(java.util.Locale.ROOT, "Goal progress: %.0f/15", goalScore));
        factors.add(String.format(java.util.Locale.ROOT, "Recurring burden: %.0f/15", recurringScore));
        return new FinancialHealthScore(score, grade(score), factors, savingsScore, spendingScore,
                budgetScore, goalScore, recurringScore);
    }

    private double scoreSavings(BigDecimal income, BigDecimal expenses, double maximum) {
        if (income.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        double savingsRate = income.subtract(expenses).multiply(BigDecimal.valueOf(100))
                .divide(income, 4, RoundingMode.HALF_UP).doubleValue();
        double score = savingsRate >= 30 ? 100 : savingsRate >= 20 ? 85 : savingsRate >= 10 ? 70
                : savingsRate >= 0 ? 50 : savingsRate >= -20 ? 25 : 0;
        return maximum * score / 100;
    }

    private double scoreSpending(BigDecimal income, BigDecimal expenses, double maximum) {
        if (income.compareTo(BigDecimal.ZERO) <= 0) {
            return expenses.compareTo(BigDecimal.ZERO) == 0 ? maximum : 0;
        }
        double ratio = expenses.multiply(BigDecimal.valueOf(100))
                .divide(income, 4, RoundingMode.HALF_UP).doubleValue();
        double score = ratio <= 50 ? 100 : ratio <= 70 ? 85 : ratio <= 85 ? 70 : ratio <= 100 ? 50 : 0;
        return maximum * score / 100;
    }

    private double scoreBudgets(User user, double maximum) {
        List<Budget> budgets = budgetRepository.findByUserAndIsActiveTrue(user);
        if (budgets.isEmpty()) {
            return maximum / 2;
        }
        double total = 0;
        for (Budget budget : budgets) {
            if (budget.getLimitAmount() == null || budget.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {
                total += 50;
                continue;
            }
            BigDecimal spent = budget.getSpentAmount() == null ? BigDecimal.ZERO : budget.getSpentAmount();
            double used = spent.multiply(BigDecimal.valueOf(100))
                    .divide(budget.getLimitAmount(), 4, RoundingMode.HALF_UP).doubleValue();
            total += used <= 50 ? 100 : used <= 75 ? 85 : used <= 90 ? 70 : used <= 100 ? 50 : 20;
        }
        return maximum * total / budgets.size() / 100;
    }

    private double scoreGoals(User user, double maximum) {
        List<FinancialGoal> goals = goalRepository.findByUserOrderByTargetDateAsc(user).stream()
                .filter(goal -> !"CANCELLED".equalsIgnoreCase(goal.getGoalStatus()))
                .toList();
        if (goals.isEmpty()) {
            return maximum / 2;
        }
        double total = 0;
        for (FinancialGoal goal : goals) {
            if ("COMPLETED".equalsIgnoreCase(goal.getGoalStatus())) {
                total += 100;
            } else if (goal.getTargetAmount() != null && goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
                double progress = goal.getCurrentAmount().multiply(BigDecimal.valueOf(100))
                        .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP).doubleValue();
                total += progress >= 75 ? 100 : progress >= 50 ? 80 : progress >= 25 ? 60 : 40;
            } else {
                total += 40;
            }
        }
        return maximum * total / goals.size() / 100;
    }

    private double scoreRecurringBurden(User user, BigDecimal income, double maximum) {
        if (income.compareTo(BigDecimal.ZERO) <= 0) {
            return maximum / 2;
        }
        BigDecimal monthly = expenseRepository.findByUserOrderByExpenseDateDesc(user).stream()
                .filter(Expense::isRecurring)
                .map(this::monthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double ratio = monthly.multiply(BigDecimal.valueOf(100))
                .divide(income, 4, RoundingMode.HALF_UP).doubleValue();
        double score = ratio <= 20 ? 100 : ratio <= 35 ? 85 : ratio <= 50 ? 70 : ratio <= 70 ? 50 : 20;
        return maximum * score / 100;
    }

    private BigDecimal monthlyAmount(Expense expense) {
        BigDecimal amount = expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount();
        return switch (expense.getRecurrenceFrequency() == null ? "MONTHLY" : expense.getRecurrenceFrequency()) {
            case "DAILY" -> amount.multiply(BigDecimal.valueOf(30));
            case "WEEKLY" -> amount.multiply(BigDecimal.valueOf(4));
            case "QUARTERLY" -> amount.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case "YEARLY" -> amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> amount;
        };
    }

    private double percentage(BigDecimal total, BigDecimal value) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private String grade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "A-";
        if (score >= 70) return "B";
        if (score >= 60) return "C";
        if (score >= 50) return "D";
        return score > 0 ? "E" : "N/A";
    }

    public static class FinancialHealthScore {
        private final int score;
        private final String grade;
        private final List<String> factors;
        private final double savingsRateScore;
        private final double expenseIncomeRatioScore;
        private final double budgetAdherenceScore;
        private final double goalProgressScore;
        private final double recurringBurdenScore;

        public FinancialHealthScore(int score, String grade, List<String> factors,
                                    double savingsRateScore, double expenseIncomeRatioScore,
                                    double budgetAdherenceScore, double goalProgressScore,
                                    double recurringBurdenScore) {
            this.score = score;
            this.grade = grade;
            this.factors = factors;
            this.savingsRateScore = savingsRateScore;
            this.expenseIncomeRatioScore = expenseIncomeRatioScore;
            this.budgetAdherenceScore = budgetAdherenceScore;
            this.goalProgressScore = goalProgressScore;
            this.recurringBurdenScore = recurringBurdenScore;
        }

        public int getScore() { return score; }
        public String getGrade() { return grade; }
        public List<String> getFactors() { return factors; }
        public double getSavingsRateScore() { return savingsRateScore; }
        public double getExpenseIncomeRatioScore() { return expenseIncomeRatioScore; }
        public double getBudgetAdherenceScore() { return budgetAdherenceScore; }
        public double getGoalProgressScore() { return goalProgressScore; }
        public double getRecurringBurdenScore() { return recurringBurdenScore; }
    }
}
