package com.finance.service;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CashFlowForecastService {

    private final IncomeService incomeService;
    private final ExpenseService expenseService;

    public CashFlowForecastService(IncomeService incomeService, ExpenseService expenseService) {
        this.incomeService = incomeService;
        this.expenseService = expenseService;
    }

    public CashFlowForecast forecast(User user, YearMonth targetMonth) {
        YearMonth month = targetMonth == null ? YearMonth.now().plusMonths(1) : targetMonth;
        List<Income> incomes = incomeService.getUserIncome(user);
        List<Expense> expenses = expenseService.getUserExpenses(user);
        List<MonthlyTotal> history = new ArrayList<>();

        for (int offset = 6; offset >= 1; offset--) {
            YearMonth historyMonth = month.minusMonths(offset);
            BigDecimal income = totalIncome(incomes, historyMonth);
            BigDecimal expense = totalExpense(expenses, historyMonth);
            if (income.compareTo(BigDecimal.ZERO) > 0 || expense.compareTo(BigDecimal.ZERO) > 0) {
                history.add(new MonthlyTotal(historyMonth, income, expense));
            }
        }

        List<MonthlyTotal> recent = history.stream()
                .sorted(Comparator.comparing(MonthlyTotal::month).reversed())
                .limit(3)
                .toList();
        BigDecimal predictedIncome = average(recent.stream().map(MonthlyTotal::income).toList());
        BigDecimal predictedExpense = average(recent.stream().map(MonthlyTotal::expense).toList());
        BigDecimal predictedNet = predictedIncome.subtract(predictedExpense);
        String trend = predictedNet.compareTo(BigDecimal.ZERO) >= 0 ? "Positive" : "Deficit expected";
        int confidence = Math.min(95, 45 + recent.size() * 15);

        return new CashFlowForecast(month, predictedIncome, predictedExpense, predictedNet,
                trend, confidence, recent.size());
    }

    public List<UpcomingBill> upcomingBills(User user, int days) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(Math.max(1, days));
        List<UpcomingBill> bills = new ArrayList<>();
        for (Expense expense : expenseService.getUserExpenses(user)) {
            if (expense.isRecurring() && expense.getNextOccurrence() != null
                    && !expense.getNextOccurrence().isBefore(today)
                    && !expense.getNextOccurrence().isAfter(limit)) {
                bills.add(new UpcomingBill(expense.getDescription(), expense.getAmount(),
                        expense.getNextOccurrence(), expense.getRecurrenceFrequency()));
            }
        }
        bills.sort(Comparator.comparing(UpcomingBill::dueDate));
        return bills;
    }

    private BigDecimal totalIncome(List<Income> incomes, YearMonth month) {
        return incomes.stream()
                .filter(income -> income.getIncomeDate() != null && YearMonth.from(income.getIncomeDate()).equals(month))
                .map(Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalExpense(List<Expense> expenses, YearMonth month) {
        return expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null && YearMonth.from(expense.getExpenseDate()).equals(month))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private record MonthlyTotal(YearMonth month, BigDecimal income, BigDecimal expense) {}

    public record CashFlowForecast(YearMonth month, BigDecimal predictedIncome, BigDecimal predictedExpense,
                                   BigDecimal predictedNet, String trend, int confidence, int monthsUsed) {}

    public record UpcomingBill(String description, BigDecimal amount, LocalDate dueDate, String frequency) {}
}
