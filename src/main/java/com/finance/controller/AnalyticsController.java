package com.finance.controller;

import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.BudgetService;
import com.finance.service.CashFlowForecastService;
import com.finance.service.ExpenseService;
import com.finance.service.IncomeService;
import com.finance.service.LocalAnalyticsEngine;
import com.finance.service.TransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    private final UserRepository userRepository;
    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final LocalAnalyticsEngine analyticsEngine;
    private final TransactionService transactionService;
    private final CashFlowForecastService cashFlowForecastService;

    public AnalyticsController(UserRepository userRepository,
                               IncomeService incomeService,
                               ExpenseService expenseService,
                               BudgetService budgetService,
                               LocalAnalyticsEngine analyticsEngine,
                               TransactionService transactionService,
                               CashFlowForecastService cashFlowForecastService) {
        this.userRepository = userRepository;
        this.incomeService = incomeService;
        this.expenseService = expenseService;
        this.budgetService = budgetService;
        this.analyticsEngine = analyticsEngine;
        this.transactionService = transactionService;
        this.cashFlowForecastService = cashFlowForecastService;
    }

    @GetMapping
    public String analytics(
            Authentication authentication,
            Model model,
            @RequestParam(value = "range", required = false, defaultValue = "this-month") String range,
            @RequestParam(value = "startDate", required = false) String startDateParam,
            @RequestParam(value = "endDate", required = false) String endDateParam) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate startDate = parseStartDate(range, startDateParam);
        LocalDate endDate = parseEndDate(range, endDateParam);

        List<Income> allIncome = incomeService.getUserIncome(user);
        List<Expense> allExpenses = expenseService.getUserExpenses(user);
        List<Budget> allBudgets = budgetService.getUserAllBudgets(user);
        List<ImportedTransaction> importedTransactions = transactionService.getUserTransactions(user);

        List<Income> filteredIncome = filterIncomeByDate(allIncome, startDate, endDate);
        List<Expense> filteredExpenses = filterExpensesByDate(allExpenses, startDate, endDate);

        BigDecimal totalIncome = filteredIncome.stream()
                .map(income -> incomeAmount(income))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
        BigDecimal totalExpenses = filteredExpenses.stream()
                .map(expense -> expenseAmount(expense))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
        BigDecimal netBalance = totalIncome.subtract(totalExpenses);

        Map<String, BigDecimal> categorySpending = getCategorySpending(filteredExpenses);
        Map<String, BigDecimal> categoryIncome = getCategoryIncome(filteredIncome);
        Map<String, BigDecimal> monthlyTrend = getMonthlyTrend(allIncome, allExpenses, 6);
        Map<String, BigDecimal> categoryTrends = analyticsEngine.calculateCategoryTrends(importedTransactions);

        List<Map<String, Object>> budgetUsage = getBudgetUsage(allBudgets, allExpenses, startDate, endDate);
        Map<String, Object> highestSpending = getHighestSpending(filteredExpenses);
        BigDecimal previousMonthComparison = getPreviousMonthComparison(allExpenses);

        model.addAttribute("user", user);
        model.addAttribute("selectedRange", range);
        model.addAttribute("startDate", startDate.toString());
        model.addAttribute("endDate", endDate.toString());
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("netBalance", netBalance);
        model.addAttribute("categorySpending", categorySpending);
        model.addAttribute("categoryIncome", categoryIncome);
        model.addAttribute("monthlyTrend", monthlyTrend);
        model.addAttribute("categoryTrends", categoryTrends);
        model.addAttribute("budgetUsage", budgetUsage);
        model.addAttribute("highestSpendingCategory", highestSpending.get("category"));
        model.addAttribute("highestSpendingAmount", highestSpending.get("amount"));
        model.addAttribute("highestSpendingMerchant", highestSpending.get("merchant"));
        model.addAttribute("previousMonthComparison", previousMonthComparison);
        model.addAttribute("cashFlowForecast", cashFlowForecastService.forecast(user, YearMonth.now().plusMonths(1)));
        model.addAttribute("upcomingBills", cashFlowForecastService.upcomingBills(user, 30));
        model.addAttribute("hasData", !filteredIncome.isEmpty() || !filteredExpenses.isEmpty());

        return "analytics";
    }

    private LocalDate parseStartDate(String range, String startDateParam) {
        YearMonth currentMonth = YearMonth.now();
        if ("custom".equals(range) && startDateParam != null && !startDateParam.isBlank()) {
            return LocalDate.parse(startDateParam);
        }
        switch (range) {
            case "last-month":
                return currentMonth.minusMonths(1).atDay(1);
            case "last-3-months":
                return currentMonth.minusMonths(3).atDay(1);
            case "last-6-months":
                return currentMonth.minusMonths(6).atDay(1);
            case "this-year":
                return currentMonth.atDay(1).withMonth(1);
            case "this-month":
            default:
                return currentMonth.atDay(1);
        }
    }

    private LocalDate parseEndDate(String range, String endDateParam) {
        YearMonth currentMonth = YearMonth.now();
        if ("custom".equals(range) && endDateParam != null && !endDateParam.isBlank()) {
            return LocalDate.parse(endDateParam);
        }
        switch (range) {
            case "last-month":
                return currentMonth.minusMonths(1).atEndOfMonth();
            case "last-3-months":
                return currentMonth.atEndOfMonth();
            case "last-6-months":
                return currentMonth.atEndOfMonth();
            case "this-year":
                return currentMonth.atEndOfMonth();
            case "this-month":
            default:
                return currentMonth.atEndOfMonth();
        }
    }

    private List<Income> filterIncomeByDate(List<Income> income, LocalDate start, LocalDate end) {
        return income.stream()
                .filter(i -> i.getIncomeDate() != null)
                .filter(i -> !i.getIncomeDate().isBefore(start) && !i.getIncomeDate().isAfter(end))
                .toList();
    }

    private List<Expense> filterExpensesByDate(List<Expense> expenses, LocalDate start, LocalDate end) {
        return expenses.stream()
                .filter(e -> e.getExpenseDate() != null)
                .filter(e -> !e.getExpenseDate().isBefore(start) && !e.getExpenseDate().isAfter(end))
                .toList();
    }

    private Map<String, BigDecimal> getCategorySpending(List<Expense> expenses) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Expense e : expenses) {
            String cat = e.getCategory() != null ? e.getCategory() : "Other";
            map.merge(cat, expenseAmount(e), (a, b) -> addAmounts(a, b));
        }
        return sortedByValueDescending(map);
    }

    private Map<String, BigDecimal> getCategoryIncome(List<Income> income) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Income i : income) {
            String cat = i.getCategory() != null ? i.getCategory() : "Other";
            map.merge(cat, incomeAmount(i), (a, b) -> addAmounts(a, b));
        }
        return sortedByValueDescending(map);
    }

    private Map<String, BigDecimal> sortedByValueDescending(Map<String, BigDecimal> values) {
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());
        Map<String, BigDecimal> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private Map<String, BigDecimal> getMonthlyTrend(List<Income> income, List<Expense> expenses, int months) {
        Map<String, BigDecimal> trend = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();

        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            String key = month.toString();
            BigDecimal incomeSum = income.stream()
                    .filter(inc -> inc.getIncomeDate() != null && YearMonth.from(inc.getIncomeDate()).equals(month))
                    .map(inc -> incomeAmount(inc))
                    .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
            BigDecimal expenseSum = expenses.stream()
                    .filter(exp -> exp.getExpenseDate() != null && YearMonth.from(exp.getExpenseDate()).equals(month))
                    .map(exp -> expenseAmount(exp))
                    .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
            trend.put(key + "-income", incomeSum);
            trend.put(key + "-expense", expenseSum);
        }
        return trend;
    }

    private List<Map<String, Object>> getBudgetUsage(List<Budget> budgets, List<Expense> expenses, LocalDate start, LocalDate end) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget.getBudgetMonth() == null) continue;
            YearMonth budgetMonth = YearMonth.parse(budget.getBudgetMonth());
            LocalDate budgetStart = budgetMonth.atDay(1);
            LocalDate budgetEnd = budgetMonth.atEndOfMonth();

            if (budgetEnd.isBefore(start) || budgetStart.isAfter(end)) continue;

            BigDecimal spent = expenses.stream()
                    .filter(e -> e.getCategory() != null && e.getCategory().equals(budget.getCategory()))
                    .filter(e -> e.getExpenseDate() != null)
                    .filter(e -> !e.getExpenseDate().isBefore(budgetStart) && !e.getExpenseDate().isAfter(budgetEnd))
                    .map(e -> expenseAmount(e))
                    .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

            BigDecimal limit = budget.getLimitAmount() != null ? budget.getLimitAmount() : BigDecimal.ZERO;
            double percentage = limit.compareTo(BigDecimal.ZERO) > 0
                    ? spent.divide(limit, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;

            Map<String, Object> item = new HashMap<>();
            item.put("category", budget.getCategory());
            item.put("limit", limit);
            item.put("spent", spent);
            item.put("remaining", limit.subtract(spent));
            item.put("percentage", percentage);
            item.put("isOver", spent.compareTo(limit) > 0);
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> getHighestSpending(List<Expense> expenses) {
        Map<String, Object> result = new HashMap<>();
        result.put("category", "N/A");
        result.put("amount", BigDecimal.ZERO);
        result.put("merchant", "N/A");

        if (expenses.isEmpty()) return result;

        Map<String, BigDecimal> byCategory = new HashMap<>();
        Map<String, BigDecimal> byMerchant = new HashMap<>();

        for (Expense e : expenses) {
            String cat = e.getCategory() != null ? e.getCategory() : "Other";
            String merchant = e.getDescription() != null ? e.getDescription() : "Unknown";
            byCategory.merge(cat, expenseAmount(e), (a, b) -> addAmounts(a, b));
            byMerchant.merge(merchant, expenseAmount(e), (a, b) -> addAmounts(a, b));
        }

        byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> {
                    result.put("category", e.getKey());
                    result.put("amount", e.getValue());
                });

        byMerchant.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> result.put("merchant", e.getKey()));

        return result;
    }

    private BigDecimal expenseAmount(Expense expense) {
        return Objects.requireNonNullElse(expense.getAmount(), BigDecimal.ZERO);
    }

    private BigDecimal incomeAmount(Income income) {
        return Objects.requireNonNullElse(income.getAmount(), BigDecimal.ZERO);
    }

    private BigDecimal addAmounts(BigDecimal left, BigDecimal right) {
        return Objects.requireNonNullElse(left, BigDecimal.ZERO)
                .add(Objects.requireNonNullElse(right, BigDecimal.ZERO));
    }

    private BigDecimal getPreviousMonthComparison(List<Expense> allExpenses) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        BigDecimal currentTotal = allExpenses.stream()
                .filter(e -> e.getExpenseDate() != null)
                .filter(e -> YearMonth.from(e.getExpenseDate()).equals(currentMonth))
                .map(e -> expenseAmount(e))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        BigDecimal previousTotal = allExpenses.stream()
                .filter(e -> e.getExpenseDate() != null)
                .filter(e -> YearMonth.from(e.getExpenseDate()).equals(previousMonth))
                .map(e -> expenseAmount(e))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        return currentTotal.subtract(previousTotal);
    }
}