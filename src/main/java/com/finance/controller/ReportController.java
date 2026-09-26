package com.finance.controller;

import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.BudgetService;
import com.finance.service.ExpenseService;
import com.finance.service.FinancialGoalService;
import com.finance.service.IncomeService;
import com.finance.service.LocalForecastEngine;
import com.finance.service.excel.LocalExcelStorageService;
import lombok.AllArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/reports")
@AllArgsConstructor
public class ReportController {

    private static final Path REPORT_ROOT = Path.of("data", "reports").toAbsolutePath().normalize();
    private static final Set<String> REPORT_TYPES = Set.of(
            "summary", "income", "expenses", "category", "budget", "trend", "forecast", "health", "insights"
    );
    private static final Set<String> PERIODS = Set.of(
            "this-month", "last-month", "last-3-months", "last-6-months", "this-year"
    );
    private static final Set<String> FORMATS = Set.of("CSV", "XLSX", "PDF");
    private static final String REPORT_ID_PATTERN = "RPT-[0-9]+-[A-F0-9]{8}";

    private final UserRepository userRepository;
    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final FinancialGoalService financialGoalService;
    private final LocalForecastEngine forecastEngine;
    private final LocalExcelStorageService storageService;

    @GetMapping
    public String reports(Authentication authentication, Model model,
                          @RequestParam(value = "type", required = false, defaultValue = "summary") String type,
                          @RequestParam(value = "period", required = false, defaultValue = "this-month") String period,
                          @RequestParam(value = "format", required = false, defaultValue = "CSV") String format) {
        User user = getUser(authentication);
        String selectedType = normalizeReportType(type);
        String selectedPeriod = normalizePeriod(period);
        String selectedFormat = normalizeFormat(format);
        ReportPeriod reportPeriod = resolvePeriod(selectedPeriod);

        model.addAttribute("user", user);
        model.addAttribute("selectedType", selectedType);
        model.addAttribute("selectedPeriod", selectedPeriod);
        model.addAttribute("selectedFormat", selectedFormat);
        model.addAttribute("startDate", reportPeriod.startDate().toString());
        model.addAttribute("endDate", reportPeriod.endDate().toString());
        model.addAttribute("reportData", buildReport(user, selectedType, reportPeriod));

        return "reports";
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateReport(Authentication authentication,
                                                 @RequestParam String reportType,
                                                 @RequestParam String period,
                                                 @RequestParam String format) {
        return currentReportResponse(authentication, reportType, period, format);
    }

    @GetMapping("/download-current")
    public ResponseEntity<byte[]> downloadCurrentReport(Authentication authentication,
                                                         @RequestParam(value = "type", required = false, defaultValue = "summary") String type,
                                                         @RequestParam(value = "period", required = false, defaultValue = "this-month") String period,
                                                         @RequestParam(value = "format", required = false, defaultValue = "CSV") String format) {
        return currentReportResponse(authentication, type, period, format);
    }

    @GetMapping("/download/{reportId}")
    public ResponseEntity<Resource> downloadReport(Authentication authentication, @PathVariable String reportId) {
        if (!reportId.matches(REPORT_ID_PATTERN)) {
            return ResponseEntity.notFound().build();
        }

        User user = getUser(authentication);
        Map<String, String> metadata = findReportMetadata(reportId).orElse(null);
        if (metadata == null || !user.getId().toString().equals(metadata.get("UserID"))
                || !"GENERATED".equals(metadata.get("Status"))) {
            return ResponseEntity.notFound().build();
        }

        String format = normalizeFormat(metadata.get("Format"));
        if (!FORMATS.contains(format)) {
            return ResponseEntity.notFound().build();
        }

        Path reportPath = REPORT_ROOT.resolve(user.getId().toString())
                .resolve(reportId + "." + extensionFor(format)).normalize();
        if (!reportPath.startsWith(REPORT_ROOT) || !Files.isRegularFile(reportPath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentTypeFor(format));
            headers.setContentLength(Files.size(reportPath));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("finwise-" + reportId.toLowerCase(Locale.ROOT) + "." + extensionFor(format), StandardCharsets.UTF_8)
                    .build());
            headers.setCacheControl("no-store");
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(reportPath));
        } catch (IOException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private String normalizeReportType(String value) {
        return value == null ? "summary" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePeriod(String value) {
        return value == null ? "this-month" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFormat(String value) {
        return value == null ? "CSV" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseEntity<byte[]> currentReportResponse(Authentication authentication, String type,
                                                           String period, String format) {
        String selectedType = normalizeReportType(type);
        String selectedPeriod = normalizePeriod(period);
        String selectedFormat = normalizeFormat(format);
        if (!REPORT_TYPES.contains(selectedType) || !PERIODS.contains(selectedPeriod) || !FORMATS.contains(selectedFormat)) {
            return ResponseEntity.badRequest().build();
        }

        User user = getUser(authentication);
        LocalDate generatedDate = LocalDate.now();
        ReportPeriod reportPeriod = resolvePeriod(selectedPeriod);
        Map<String, Object> reportData = buildReport(user, selectedType, reportPeriod);
        ReportDefinition definition = buildReportDefinition(user, selectedType, selectedPeriod, reportPeriod, generatedDate, reportData);

        try {
            byte[] content = renderReport(selectedFormat, definition);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentTypeFor(selectedFormat));
            headers.setContentLength(content.length);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("FinWise-Financial-Report-" + generatedDate + "." + extensionFor(selectedFormat),
                            StandardCharsets.UTF_8)
                    .build());
            headers.setCacheControl("no-store");
            return ResponseEntity.ok().headers(headers).body(content);
        } catch (IOException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private ReportPeriod resolvePeriod(String period) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        return switch (period) {
            case "last-month" -> new ReportPeriod(
                    currentMonth.minusMonths(1).atDay(1),
                    currentMonth.minusMonths(1).atEndOfMonth()
            );
            case "last-3-months" -> new ReportPeriod(
                    currentMonth.minusMonths(2).atDay(1),
                    today
            );
            case "last-6-months" -> new ReportPeriod(
                    currentMonth.minusMonths(5).atDay(1),
                    today
            );
            case "this-year" -> new ReportPeriod(
                    currentMonth.atDay(1).withMonth(1),
                    today
            );
            default -> new ReportPeriod(currentMonth.atDay(1), today);
        };
    }

    private Map<String, Object> buildReport(User user, String type, ReportPeriod period) {
        return switch (type) {
            case "income" -> generateIncomeReport(user, period);
            case "expenses" -> generateExpenseReport(user, period);
            case "category" -> generateCategoryReport(user, period);
            case "budget" -> generateBudgetReport(user, period);
            case "trend" -> generateTrendReport(user, period);
            case "forecast" -> generateForecastReport(user, period);
            case "health" -> generateHealthReport(user, period);
            case "insights" -> generateInsightsReport(user, period);
            default -> generateSummaryReport(user, period);
        };
    }

    private Map<String, Object> generateSummaryReport(User user, ReportPeriod period) {
        List<Income> incomes = incomeService.getIncomeByDateRange(user, period.startDate(), period.endDate());
        List<Expense> expenses = expenseService.getExpensesByDateRange(user, period.startDate(), period.endDate());
        List<BudgetSummary> budgets = buildBudgetSummaries(user, period, expenseService.getUserExpenses(user));
        List<FinancialGoal> goals = goalsAsOf(user, period.endDate());
        BigDecimal totalIncome = totalIncome(incomes);
        BigDecimal totalExpenses = totalExpenses(expenses);
        BigDecimal netBalance = totalIncome.subtract(totalExpenses);
        BigDecimal savingsRate = totalIncome.compareTo(BigDecimal.ZERO) > 0
                ? netBalance.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> report = new HashMap<>();
        report.put("totalIncome", totalIncome);
        report.put("totalExpenses", totalExpenses);
        report.put("netBalance", netBalance);
        report.put("savingsRate", savingsRate);
        report.put("budgetCount", (int) budgets.stream().filter(BudgetSummary::isActive).count());
        report.put("overBudgetCount", (int) budgets.stream().filter(budget -> budget.isOverBudget()).count());
        report.put("activeGoals", (int) goals.stream().filter(goal -> isActiveGoal(goal)).count());
        report.put("completedGoals", (int) goals.stream().filter(this::isCompletedGoal).count());
        report.put("transactionCount", incomes.size() + expenses.size());
        report.put("hasData", !incomes.isEmpty() || !expenses.isEmpty() || !budgets.isEmpty() || !goals.isEmpty());
        return report;
    }

    private Map<String, Object> generateIncomeReport(User user, ReportPeriod period) {
        List<Income> incomes = incomeService.getIncomeByDateRange(user, period.startDate(), period.endDate());
        BigDecimal totalIncome = totalIncome(incomes);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> bySource = new LinkedHashMap<>();

        for (Income income : incomes) {
            BigDecimal amount = incomeAmount(income);
            byCategory.merge(displayValue(income.getCategory(), "Uncategorized"), amount, BigDecimal::add);
            bySource.merge(displayValue(income.getIncomeSource(), "Unknown"), amount, BigDecimal::add);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("totalIncome", totalIncome);
        report.put("incomeCount", incomes.size());
        report.put("averageIncome", average(totalIncome, incomes.size()));
        report.put("byCategory", sortAmountMap(byCategory));
        report.put("bySource", sortAmountMap(bySource));
        report.put("incomes", incomes);
        report.put("hasData", !incomes.isEmpty());
        return report;
    }

    private Map<String, Object> generateExpenseReport(User user, ReportPeriod period) {
        List<Expense> expenses = expenseService.getExpensesByDateRange(user, period.startDate(), period.endDate());
        BigDecimal totalExpenses = totalExpenses(expenses);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> byDescription = new LinkedHashMap<>();
        Map<String, BigDecimal> byPaymentMethod = new LinkedHashMap<>();

        for (Expense expense : expenses) {
            BigDecimal amount = expenseAmount(expense);
            byCategory.merge(displayValue(expense.getCategory(), "Uncategorized"), amount, BigDecimal::add);
            byDescription.merge(displayValue(expense.getDescription(), "Unknown"), amount, BigDecimal::add);
            byPaymentMethod.merge(displayValue(expense.getPaymentMethod(), "Unknown"), amount, BigDecimal::add);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("totalExpenses", totalExpenses);
        report.put("expenseCount", expenses.size());
        report.put("averageExpense", average(totalExpenses, expenses.size()));
        report.put("byCategory", sortAmountMap(byCategory));
        report.put("byDescription", sortAmountMap(byDescription));
        report.put("byPaymentMethod", sortAmountMap(byPaymentMethod));
        report.put("expenses", expenses);
        report.put("hasData", !expenses.isEmpty());
        return report;
    }

    private Map<String, Object> generateCategoryReport(User user, ReportPeriod period) {
        List<Income> incomes = incomeService.getIncomeByDateRange(user, period.startDate(), period.endDate());
        List<Expense> expenses = expenseService.getExpensesByDateRange(user, period.startDate(), period.endDate());
        Map<String, CategorySummary> categoryMap = new LinkedHashMap<>();

        for (Income income : incomes) {
            categoryMap.computeIfAbsent(displayValue(income.getCategory(), "Uncategorized"), CategorySummary::new)
                    .addIncome(incomeAmount(income));
        }
        for (Expense expense : expenses) {
            categoryMap.computeIfAbsent(displayValue(expense.getCategory(), "Uncategorized"), CategorySummary::new)
                    .addExpense(expenseAmount(expense));
        }

        List<CategorySummary> categories = new ArrayList<>(categoryMap.values());
        categories.sort(Comparator.comparing(CategorySummary::getNetAmount).reversed());
        Map<String, Object> report = new HashMap<>();
        report.put("categories", categories);
        report.put("totalCategories", categories.size());
        report.put("totalIncome", totalIncome(incomes));
        report.put("totalExpenses", totalExpenses(expenses));
        report.put("hasData", !categories.isEmpty());
        return report;
    }

    private Map<String, Object> generateBudgetReport(User user, ReportPeriod period) {
        List<BudgetSummary> budgets = buildBudgetSummaries(user, period, expenseService.getUserExpenses(user));
        BigDecimal totalBudget = budgets.stream().map(BudgetSummary::getLimitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = budgets.stream().map(BudgetSummary::getSpentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemaining = totalBudget.subtract(totalSpent);
        BigDecimal overallPercentage = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> report = new HashMap<>();
        report.put("budgets", budgets);
        report.put("totalBudget", totalBudget);
        report.put("totalSpent", totalSpent);
        report.put("totalRemaining", totalRemaining);
        report.put("overallPercentage", overallPercentage);
        report.put("overBudgetCount", (int) budgets.stream().filter(BudgetSummary::isOverBudget).count());
        report.put("hasData", !budgets.isEmpty());
        return report;
    }

    private List<BudgetSummary> buildBudgetSummaries(User user, ReportPeriod period, List<Expense> allExpenses) {
        List<BudgetSummary> summaries = new ArrayList<>();
        for (Budget budget : budgetService.getUserAllBudgets(user)) {
            if (budget.getBudgetMonth() == null) {
                continue;
            }
            try {
                YearMonth budgetMonth = YearMonth.parse(budget.getBudgetMonth().trim());
                if (budgetMonth.atEndOfMonth().isBefore(period.startDate())
                        || budgetMonth.atDay(1).isAfter(period.endDate())) {
                    continue;
                }

                BigDecimal spent = allExpenses.stream()
                        .filter(expense -> YearMonth.from(expense.getExpenseDate()).equals(budgetMonth))
                        .filter(expense -> sameCategory(expense.getCategory(), budget.getCategory()))
                        .map(this::expenseAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal limit = amountOrZero(budget.getLimitAmount());
                summaries.add(new BudgetSummary(
                        displayValue(budget.getCategory(), "Uncategorized"),
                        budgetMonth.toString(),
                        limit,
                        spent,
                        !Boolean.FALSE.equals(budget.getIsActive())
                ));
            } catch (java.time.DateTimeException ignored) {
            }
        }
        summaries.sort(Comparator.comparing(BudgetSummary::getBudgetMonth)
                .thenComparing(BudgetSummary::getCategory));
        return summaries;
    }

    private Map<String, Object> generateTrendReport(User user, ReportPeriod period) {
        List<Income> allIncomes = incomeService.getUserIncome(user);
        List<Expense> allExpenses = expenseService.getUserExpenses(user);
        YearMonth startMonth = YearMonth.from(period.startDate());
        YearMonth endMonth = YearMonth.from(period.endDate());
        int monthCount = (int) startMonth.until(endMonth.plusMonths(1), ChronoUnit.MONTHS);
        Map<String, BigDecimal> monthlyIncome = new LinkedHashMap<>();
        Map<String, BigDecimal> monthlyExpenses = new LinkedHashMap<>();
        boolean hasData = false;

        for (int index = 0; index < monthCount; index++) {
            YearMonth month = startMonth.plusMonths(index);
            LocalDate monthStart = month.atDay(1).isBefore(period.startDate()) ? period.startDate() : month.atDay(1);
            LocalDate monthEnd = month.atEndOfMonth().isAfter(period.endDate()) ? period.endDate() : month.atEndOfMonth();
            BigDecimal income = totalIncome(filterByDate(allIncomes, monthStart, monthEnd));
            BigDecimal expenses = totalExpenses(filterExpensesByDate(allExpenses, monthStart, monthEnd));
            monthlyIncome.put(month.toString(), income);
            monthlyExpenses.put(month.toString(), expenses);
            hasData |= income.compareTo(BigDecimal.ZERO) > 0 || expenses.compareTo(BigDecimal.ZERO) > 0;
        }

        Map<String, Object> report = new HashMap<>();
        report.put("monthlyIncome", monthlyIncome);
        report.put("monthlyExpenses", monthlyExpenses);
        report.put("months", monthCount);
        report.put("hasData", hasData);
        return report;
    }

    private Map<String, Object> generateForecastReport(User user, ReportPeriod period) {
        List<Income> incomes = incomeService.getIncomeByDateRange(user, period.startDate(), period.endDate());
        List<Expense> expenses = expenseService.getExpensesByDateRange(user, period.startDate(), period.endDate());
        List<ImportedTransaction> transactions = new ArrayList<>();

        for (Income income : incomes) {
            ImportedTransaction transaction = new ImportedTransaction();
            transaction.setUser(user);
            transaction.setAmount(incomeAmount(income));
            transaction.setTransactionDate(income.getIncomeDate());
            transaction.setTransactionType("INCOME");
            transaction.setCategory(income.getCategory());
            transactions.add(transaction);
        }
        for (Expense expense : expenses) {
            ImportedTransaction transaction = new ImportedTransaction();
            transaction.setUser(user);
            transaction.setAmount(expenseAmount(expense));
            transaction.setTransactionDate(expense.getExpenseDate());
            transaction.setTransactionType("EXPENSE");
            transaction.setCategory(expense.getCategory());
            transactions.add(transaction);
        }

        YearMonth forecastMonth = YearMonth.from(period.endDate()).plusMonths(1);
        var forecast = forecastEngine.forecastForMonth(transactions, forecastMonth);
        YearMonth currentMonth = YearMonth.from(period.endDate());
        BigDecimal currentSpending = totalExpenses(filterExpensesByDate(
                expenseService.getUserExpenses(user), currentMonth.atDay(1), currentMonth.atEndOfMonth()));
        BigDecimal previousSpending = totalExpenses(filterExpensesByDate(
                expenseService.getUserExpenses(user), currentMonth.minusMonths(1).atDay(1),
                currentMonth.minusMonths(1).atEndOfMonth()));

        Map<String, Object> report = new HashMap<>();
        report.put("predictedSpending", forecast.getPredictedSpending());
        report.put("currentSpending", currentSpending);
        report.put("previousMonthComparison", currentSpending.subtract(previousSpending));
        report.put("trend", forecast.getTrend());
        report.put("confidence", forecast.getConfidence());
        report.put("forecastMonth", forecastMonth.toString());
        report.put("hasData", !transactions.isEmpty());
        return report;
    }

    private Map<String, Object> generateHealthReport(User user, ReportPeriod period) {
        List<Income> incomes = incomeService.getIncomeByDateRange(user, period.startDate(), period.endDate());
        List<Expense> expenseRecords = expenseService.getExpensesByDateRange(user, period.startDate(), period.endDate());
        List<BudgetSummary> budgets = buildBudgetSummaries(user, period, expenseService.getUserExpenses(user));
        List<FinancialGoal> goals = goalsAsOf(user, period.endDate());
        BigDecimal income = totalIncome(incomes);
        BigDecimal expenses = totalExpenses(expenseRecords);
        boolean hasData = !incomes.isEmpty() || !expenseRecords.isEmpty();

        if (!hasData) {
            return Map.of(
                    "score", 0,
                    "grade", "N/A",
                    "factors", List.of("Not enough transaction data for the selected period."),
                    "savingsRateScore", BigDecimal.ZERO,
                    "expenseIncomeRatioScore", BigDecimal.ZERO,
                    "budgetAdherenceScore", BigDecimal.ZERO,
                    "goalProgressScore", BigDecimal.ZERO,
                    "recurringBurdenScore", BigDecimal.ZERO,
                    "hasData", false
            );
        }

        BigDecimal net = income.subtract(expenses);
        BigDecimal savingsRate = income.compareTo(BigDecimal.ZERO) > 0
                ? net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(-100);
        BigDecimal savingsScore = decimalScore(BigDecimal.valueOf(50).add(savingsRate.multiply(BigDecimal.valueOf(2))));
        BigDecimal expenseScore = income.compareTo(BigDecimal.ZERO) > 0
                ? decimalScore(BigDecimal.valueOf(100).subtract(expenses.multiply(BigDecimal.valueOf(100))
                .divide(income, 1, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(0.5))))
                : BigDecimal.ZERO;
        long activeBudgets = budgets.stream().filter(BudgetSummary::isActive).count();
        long overBudgets = budgets.stream().filter(BudgetSummary::isOverBudget).count();
        BigDecimal budgetScore = activeBudgets == 0
                ? BigDecimal.valueOf(75)
                : decimalScore(BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(overBudgets)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(activeBudgets), 1, RoundingMode.HALF_UP)));
        BigDecimal goalScore = averageGoalProgress(goals);
        BigDecimal score = savingsScore.multiply(BigDecimal.valueOf(0.35))
                .add(expenseScore.multiply(BigDecimal.valueOf(0.30)))
                .add(budgetScore.multiply(BigDecimal.valueOf(0.20)))
                .add(goalScore.multiply(BigDecimal.valueOf(0.15)))
                .setScale(0, RoundingMode.HALF_UP);
        int numericScore = score.intValue();
        List<String> factors = List.of(
                "Savings rate: " + savingsRate.setScale(1, RoundingMode.HALF_UP) + "%",
                income.compareTo(BigDecimal.ZERO) > 0
                        ? "Expense-to-income ratio: " + decimalScore(expenses.multiply(BigDecimal.valueOf(100))
                        .divide(income, 1, RoundingMode.HALF_UP)).setScale(1, RoundingMode.HALF_UP) + "%"
                        : "No income recorded for this period",
                "Budgets over limit: " + overBudgets + " of " + activeBudgets,
                "Average goal progress: " + goalScore.setScale(1, RoundingMode.HALF_UP) + "%"
        );

        Map<String, Object> report = new HashMap<>();
        report.put("score", numericScore);
        report.put("grade", healthGrade(numericScore));
        report.put("factors", factors);
        report.put("savingsRateScore", savingsScore);
        report.put("expenseIncomeRatioScore", expenseScore);
        report.put("budgetAdherenceScore", budgetScore);
        report.put("goalProgressScore", goalScore);
        report.put("recurringBurdenScore", BigDecimal.valueOf(100));
        report.put("hasData", true);
        return report;
    }

    private Map<String, Object> generateInsightsReport(User user, ReportPeriod period) {
        List<Income> incomes = incomeService.getIncomeByDateRange(user, period.startDate(), period.endDate());
        List<Expense> expenses = expenseService.getExpensesByDateRange(user, period.startDate(), period.endDate());
        BigDecimal income = totalIncome(incomes);
        BigDecimal expensesTotal = totalExpenses(expenses);
        BigDecimal net = income.subtract(expensesTotal);
        long monthCount = ChronoUnit.MONTHS.between(YearMonth.from(period.startDate()), YearMonth.from(period.endDate())) + 1;
        ReportPeriod previousPeriod = new ReportPeriod(period.startDate().minusMonths(monthCount),
                period.startDate().minusDays(1));
        BigDecimal previousIncome = totalIncome(incomeService.getIncomeByDateRange(
                user, previousPeriod.startDate(), previousPeriod.endDate()));
        BigDecimal previousExpenses = totalExpenses(expenseService.getExpensesByDateRange(
                user, previousPeriod.startDate(), previousPeriod.endDate()));
        List<BudgetSummary> budgets = buildBudgetSummaries(user, period, expenseService.getUserExpenses(user));
        List<Insight> insights = new ArrayList<>();

        if (incomes.isEmpty() && expenses.isEmpty()) {
            insights.add(new Insight("MEDIUM", "No transactions were found for the selected period.",
                    "Add income or expenses to receive personalized financial insights."));
        } else {
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                insights.add(new Insight("CRITICAL", "Expenses exceeded income during this period.",
                        "Review variable spending and create a spending limit for the largest expense category."));
            }
            if (previousExpenses.compareTo(BigDecimal.ZERO) > 0
                    && expensesTotal.compareTo(previousExpenses.multiply(BigDecimal.valueOf(1.10))) > 0) {
                BigDecimal change = expensesTotal.subtract(previousExpenses)
                        .multiply(BigDecimal.valueOf(100)).divide(previousExpenses, 1, RoundingMode.HALF_UP);
                insights.add(new Insight("HIGH", "Spending increased by " + change + "% versus the previous period.",
                        "Check recent large purchases and set category budgets for the next period."));
            }
            if (previousIncome.compareTo(BigDecimal.ZERO) > 0
                    && income.compareTo(previousIncome.multiply(BigDecimal.valueOf(0.90))) < 0) {
                BigDecimal change = previousIncome.subtract(income)
                        .multiply(BigDecimal.valueOf(100)).divide(previousIncome, 1, RoundingMode.HALF_UP);
                insights.add(new Insight("HIGH", "Income decreased by " + change + "% versus the previous period.",
                        "Confirm expected income and plan essential expenses before the next pay cycle."));
            }
            if (income.compareTo(BigDecimal.ZERO) > 0
                    && net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP)
                    .compareTo(BigDecimal.valueOf(20)) >= 0) {
                insights.add(new Insight("LOW", "Savings performance is strong for this period.",
                        "Move part of the surplus into savings or toward a financial goal."));
            }
        }

        long overBudgets = budgets.stream().filter(BudgetSummary::isOverBudget).count();
        if (overBudgets > 0) {
            insights.add(new Insight("CRITICAL", overBudgets + " budget" + (overBudgets == 1 ? " is" : "s are") + " over the selected period.",
                    "Reduce spending in the affected categories or adjust the limits if they are no longer realistic."));
        }

        long overdueGoals = goalsAsOf(user, period.endDate()).stream()
                .filter(goal -> isActiveGoal(goal) && goal.getTargetDate().isBefore(period.endDate()))
                .count();
        if (overdueGoals > 0) {
            insights.add(new Insight("HIGH", overdueGoals + " financial goal" + (overdueGoals == 1 ? " is" : "s are") + " past its target date.",
                    "Update the target date or increase contributions to the affected goals."));
        }

        Map<String, Object> report = new HashMap<>();
        report.put("insights", insights);
        report.put("insightCount", insights.size());
        report.put("criticalCount", (int) insights.stream().filter(value -> "CRITICAL".equals(value.getPriority())).count());
        report.put("highCount", (int) insights.stream().filter(value -> "HIGH".equals(value.getPriority())).count());
        report.put("mediumCount", (int) insights.stream().filter(value -> "MEDIUM".equals(value.getPriority())).count());
        report.put("lowCount", (int) insights.stream().filter(value -> "LOW".equals(value.getPriority())).count());
        report.put("hasData", !insights.isEmpty());
        return report;
    }

    private List<Income> filterByDate(List<Income> incomes, LocalDate startDate, LocalDate endDate) {
        return incomes.stream()
                .filter(income -> income.getIncomeDate() != null)
                .filter(income -> !income.getIncomeDate().isBefore(startDate) && !income.getIncomeDate().isAfter(endDate))
                .toList();
    }

    private List<Expense> filterExpensesByDate(List<Expense> expenses, LocalDate startDate, LocalDate endDate) {
        return expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null)
                .filter(expense -> !expense.getExpenseDate().isBefore(startDate) && !expense.getExpenseDate().isAfter(endDate))
                .toList();
    }

    private List<FinancialGoal> goalsAsOf(User user, LocalDate endDate) {
        return financialGoalService.getUserGoals(user).stream()
                .filter(goal -> goal.getCreatedAt() == null || !goal.getCreatedAt().toLocalDate().isAfter(endDate))
                .toList();
    }

    private BigDecimal totalIncome(List<Income> incomes) {
        return incomes.stream().map(this::incomeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalExpenses(List<Expense> expenses) {
        return expenses.stream().map(this::expenseAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal incomeAmount(Income income) {
        return amountOrZero(income.getAmount());
    }

    private BigDecimal expenseAmount(Expense expense) {
        return amountOrZero(expense.getAmount());
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return Objects.requireNonNullElse(amount, BigDecimal.ZERO);
    }

    private BigDecimal average(BigDecimal total, int count) {
        return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> sortAmountMap(Map<String, BigDecimal> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left.add(right), LinkedHashMap::new));
    }

    private boolean sameCategory(String left, String right) {
        return displayValue(left, "Uncategorized").equalsIgnoreCase(displayValue(right, "Uncategorized"));
    }

    private String displayValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean isCompletedGoal(FinancialGoal goal) {
        return "COMPLETED".equalsIgnoreCase(goal.getGoalStatus())
                || amountOrZero(goal.getCurrentAmount()).compareTo(amountOrZero(goal.getTargetAmount())) >= 0;
    }

    private boolean isActiveGoal(FinancialGoal goal) {
        return !isCompletedGoal(goal) && !"FAILED".equalsIgnoreCase(goal.getGoalStatus());
    }

    private BigDecimal averageGoalProgress(List<FinancialGoal> goals) {
        if (goals.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return goals.stream()
                .map(goal -> amountOrZero(goal.getTargetAmount()).compareTo(BigDecimal.ZERO) > 0
                        ? amountOrZero(goal.getCurrentAmount()).multiply(BigDecimal.valueOf(100))
                        .divide(amountOrZero(goal.getTargetAmount()), 1, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(goals.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalScore(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    private String healthGrade(int score) {
        if (score >= 90) return "A+";
        if (score >= 85) return "A";
        if (score >= 80) return "A-";
        if (score >= 75) return "B+";
        if (score >= 70) return "B";
        if (score >= 65) return "B-";
        if (score >= 60) return "C+";
        if (score >= 55) return "C";
        if (score >= 50) return "C-";
        if (score >= 45) return "D+";
        if (score >= 40) return "D";
        return "F";
    }

    private ReportDefinition buildReportDefinition(User user, String type, String period,
                                                    ReportPeriod reportPeriod, LocalDate generatedDate,
                                                    Map<String, Object> reportData) {
        String title = reportTitle(type);
        List<ReportTable> tables = new ArrayList<>();
        tables.add(new ReportTable("Report Details", List.of("Field", "Value"), List.of(
                List.<Object>of("Report Type", title),
                List.<Object>of("Report Period", reportPeriod.startDate() + " to " + reportPeriod.endDate()),
                List.<Object>of("Generated Date", generatedDate),
                List.<Object>of("User", user.getUsername())
        )));

        switch (type) {
            case "income" -> {
                List<Income> incomes = incomes(reportData);
                tables.add(new ReportTable("Income Transactions", List.of(
                        "Date", "Description", "Category", "Source", "Amount", "Notes"
                ), incomes.stream().map(income -> List.<Object>of(
                        income.getIncomeDate().toString(), income.getDescription(), income.getCategory(),
                        displayValue(income.getIncomeSource(), "Unknown"), incomeAmount(income),
                        displayValue(income.getNotes(), "")
                )).toList()));
                 tables.add(new ReportTable("Income by Category", List.of("Category", "Amount"),
                         amountRows(reportData.get("byCategory"))));
                 tables.add(new ReportTable("Income by Source", List.of("Source", "Amount"),
                         amountRows(reportData.get("bySource"))));
             }
             case "expenses" -> {
                List<Expense> expenses = expenses(reportData);
                tables.add(new ReportTable("Expense Transactions", List.of(
                        "Date", "Description", "Category", "Payment Method", "Amount", "Notes"
                ), expenses.stream().map(expense -> List.<Object>of(
                        expense.getExpenseDate().toString(), expense.getDescription(), expense.getCategory(),
                        displayValue(expense.getPaymentMethod(), "Unknown"), expenseAmount(expense),
                        displayValue(expense.getNotes(), "")
                )).toList()));
                 tables.add(new ReportTable("Expenses by Category", List.of("Category", "Amount"),
                         amountRows(reportData.get("byCategory"))));
                 tables.add(new ReportTable("Expenses by Payment Method", List.of("Payment Method", "Amount"),
                         amountRows(reportData.get("byPaymentMethod"))));
             }
             case "category" -> {
                List<CategorySummary> categories = categories(reportData);
                tables.add(new ReportTable("Category Breakdown", List.of(
                        "Category", "Income", "Expenses", "Net"
                ), categories.stream().map(category -> List.<Object>of(
                        category.getName(), category.getIncomeTotal(), category.getExpenseTotal(), category.getNetAmount()
                )).toList()));
            }
            case "budget" -> {
                List<BudgetSummary> budgets = budgets(reportData);
                tables.add(new ReportTable("Budget Performance", List.of(
                        "Month", "Category", "Limit", "Actual Spending", "Remaining", "Utilization", "Status"
                ), budgets.stream().map(budget -> List.<Object>of(
                        budget.getBudgetMonth(), budget.getCategory(), budget.getLimitAmount(), budget.getSpentAmount(),
                        budget.getRemainingAmount(), budget.getPercentageUsed() + "%", budget.getAlertLevel()
                )).toList()));
            }
            case "trend" -> {
                Map<String, BigDecimal> incomesByMonth = amountMap(reportData.get("monthlyIncome"));
                Map<String, BigDecimal> expensesByMonth = amountMap(reportData.get("monthlyExpenses"));
                tables.add(new ReportTable("Monthly Spending Trend", List.of(
                        "Month", "Income", "Expenses", "Net"
                ), incomesByMonth.entrySet().stream().map(entry -> List.<Object>of(
                        entry.getKey(), entry.getValue(), expensesByMonth.getOrDefault(entry.getKey(), BigDecimal.ZERO),
                        entry.getValue().subtract(expensesByMonth.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                )).toList()));
            }
            case "forecast" -> tables.add(new ReportTable("Spending Forecast", List.of(
                    "Forecast Month", "Predicted Spending", "Current Spending", "Previous Month Difference",
                    "Trend", "Confidence"
            ), List.of(List.<Object>of(
                    reportData.get("forecastMonth"), reportData.get("predictedSpending"), reportData.get("currentSpending"),
                    reportData.get("previousMonthComparison"), reportData.get("trend"),
                    percentage(reportData.get("confidence"))
            ))));
            case "health" -> {
                Map<String, BigDecimal> scoreTable = new LinkedHashMap<>();
                scoreTable.put("Savings", toDecimal(reportData.get("savingsRateScore")));
                scoreTable.put("Expense Control", toDecimal(reportData.get("expenseIncomeRatioScore")));
                scoreTable.put("Budget Adherence", toDecimal(reportData.get("budgetAdherenceScore")));
                scoreTable.put("Goal Progress", toDecimal(reportData.get("goalProgressScore")));
                tables.add(new ReportTable("Health Score", List.of("Score", "Grade"), List.of(
                        List.<Object>of(reportData.get("score"), reportData.get("grade"))
                )));
                tables.add(new ReportTable("Health Components", List.of("Component", "Score"),
                        amountRows(scoreTable)));
            }
            case "insights" -> {
                List<Insight> insights = insights(reportData);
                tables.add(new ReportTable("Smart Financial Insights", List.of("Priority", "Finding", "Recommendation"),
                        insights.stream().map(insight -> List.<Object>of(
                                insight.getPriority(), insight.getMessage(), insight.getRecommendation()
                        )).toList()));
            }
            default -> {
                tables.add(new ReportTable("Financial Summary", List.of("Metric", "Value"), List.of(
                        List.<Object>of("Total Income", reportData.get("totalIncome")),
                        List.<Object>of("Total Expenses", reportData.get("totalExpenses")),
                        List.<Object>of("Net Balance", reportData.get("netBalance")),
                        List.<Object>of("Savings Rate", reportData.get("savingsRate") + "%"),
                        List.<Object>of("Transactions", reportData.get("transactionCount")),
                        List.<Object>of("Active Budgets", reportData.get("budgetCount")),
                        List.<Object>of("Over Budget", reportData.get("overBudgetCount")),
                        List.<Object>of("Active Goals", reportData.get("activeGoals")),
                        List.<Object>of("Completed Goals", reportData.get("completedGoals"))
                )));
            }
        }

        return new ReportDefinition(title, user.getUsername(), period,
                reportPeriod.startDate(), reportPeriod.endDate(), generatedDate, tables);
    }

    private String reportTitle(String type) {
        return switch (type) {
            case "income" -> "Income Report";
            case "expenses" -> "Expense Report";
            case "category" -> "Category Report";
            case "budget" -> "Budget Report";
            case "trend" -> "Spending Trend Report";
            case "forecast" -> "Forecast Report";
            case "health" -> "Financial Health Report";
            case "insights" -> "Smart Insights Report";
            default -> "Financial Summary";
        };
    }

    private List<List<Object>> amountRows(Object value) {
        Map<String, BigDecimal> amounts = amountMap(value);
        return amounts.entrySet().stream().map(entry -> List.<Object>of(entry.getKey(), entry.getValue())).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> amountMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, BigDecimal>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Income> incomes(Map<String, Object> reportData) {
        return (List<Income>) reportData.getOrDefault("incomes", List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Expense> expenses(Map<String, Object> reportData) {
        return (List<Expense>) reportData.getOrDefault("expenses", List.of());
    }

    @SuppressWarnings("unchecked")
    private List<CategorySummary> categories(Map<String, Object> reportData) {
        return (List<CategorySummary>) reportData.getOrDefault("categories", List.of());
    }

    @SuppressWarnings("unchecked")
    private List<BudgetSummary> budgets(Map<String, Object> reportData) {
        return (List<BudgetSummary>) reportData.getOrDefault("budgets", List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Insight> insights(Map<String, Object> reportData) {
        return (List<Insight>) reportData.getOrDefault("insights", List.of());
    }

    private BigDecimal toDecimal(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private String percentage(Object value) {
        double percentage = toDecimal(value).multiply(BigDecimal.valueOf(100)).doubleValue();
        return BigDecimal.valueOf(percentage).setScale(0, RoundingMode.HALF_UP) + "%";
    }

    private byte[] renderReport(String format, ReportDefinition definition) throws IOException {
        return switch (format) {
            case "XLSX" -> renderXlsx(definition);
            case "PDF" -> renderPdf(definition);
            default -> renderCsv(definition);
        };
    }

    private byte[] renderCsv(ReportDefinition definition) {
        StringBuilder output = new StringBuilder("\ufeff");
        appendCsvRow(output, List.of("Report Type", definition.title()));
        appendCsvRow(output, List.of("Report Period", definition.startDate() + " to " + definition.endDate()));
        appendCsvRow(output, List.of("Generated Date", definition.generatedDate()));
        appendCsvRow(output, List.of("User", definition.username()));
        output.append('\n');

        for (ReportTable table : definition.tables()) {
            appendCsvRow(output, table.headers());
            for (List<Object> row : table.rows()) {
                appendCsvRow(output, row);
            }
            output.append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvRow(StringBuilder output, List<?> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            output.append(csvValue(values.get(index)));
        }
        output.append("\r\n");
    }

    private String csvValue(Object value) {
        String text = displayValue(value == null ? "" : String.valueOf(value), "");
        if (value instanceof String && !text.isBlank() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private byte[] renderXlsx(ReportDefinition definition) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Set<String> usedSheetNames = new HashSet<>();
            for (ReportTable table : definition.tables()) {
                String sheetName = uniqueSheetName(table.title(), usedSheetNames);
                Sheet sheet = workbook.createSheet(sheetName);
                Row titleRow = sheet.createRow(0);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue("FinWise Financial Report - " + definition.title() + " - " + table.title());
                titleCell.setCellStyle(titleStyle);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, table.headers().size() - 1)));

                Row headerRow = sheet.createRow(1);
                for (int column = 0; column < table.headers().size(); column++) {
                    Cell cell = headerRow.createCell(column);
                    cell.setCellValue(table.headers().get(column));
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(column, 20 * 256);
                }

                int rowIndex = 2;
                for (List<Object> values : table.rows()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int column = 0; column < values.size(); column++) {
                        Cell cell = row.createCell(column);
                        Object value = values.get(column);
                        if (value instanceof BigDecimal amount) {
                            cell.setCellValue(amount.doubleValue());
                            cell.setCellStyle(amountStyle);
                        } else if (value instanceof Number number) {
                            cell.setCellValue(number.doubleValue());
                        } else {
                            cell.setCellValue(displayValue(value == null ? "" : String.valueOf(value), ""));
                        }
                    }
                }

                int lastRow = Math.max(1, rowIndex - 1);
                sheet.setAutoFilter(new CellRangeAddress(1, lastRow, 0, Math.max(0, table.headers().size() - 1)));
                sheet.createFreezePane(0, 2);
            }

            workbook.getProperties().getCoreProperties().setTitle("FinWise Financial Report - " + definition.title());
            workbook.getProperties().getCoreProperties().setDescription(
                    definition.startDate() + " to " + definition.endDate());
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String uniqueSheetName(String title, Set<String> usedNames) {
        String base = title.replaceAll("[\\\\/*?:\\[\\]]", " ");
        base = base.length() > 28 ? base.substring(0, 28) : base;
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            String suffixText = " " + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 31 - suffixText.length())) + suffixText;
        }
        return candidate;
    }

    private byte[] renderPdf(ReportDefinition definition) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (ReportTable table : definition.tables()) {
                int rowIndex = 0;
                do {
                    PDPage page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        float y = 740;
                        y = writePdfLine(content, "FinWise Financial Report", PDType1Font.HELVETICA_BOLD, 18, 42, y, 528);
                        y = writePdfLine(content, definition.title() + " - " + table.title(), PDType1Font.HELVETICA_BOLD, 12, 42, y, 528);
                        y = writePdfLine(content, "Report Type: " + definition.title(), PDType1Font.HELVETICA, 9, 42, y, 528);
                        y = writePdfLine(content, "Report Period: " + definition.startDate() + " to " + definition.endDate(),
                                PDType1Font.HELVETICA, 9, 42, y, 528);
                        y = writePdfLine(content, "Generated Date: " + definition.generatedDate(),
                                PDType1Font.HELVETICA, 9, 42, y, 528);
                        y = writePdfLine(content, "User: " + definition.username(), PDType1Font.HELVETICA, 9, 42, y, 528);
                        y -= 8;
                        y = writePdfLine(content, String.join(" | ", table.headers()), PDType1Font.HELVETICA_BOLD, 9, 42, y, 528);
                        y -= 4;

                        int rowsOnPage = 0;
                        while (rowIndex < table.rows().size() && rowsOnPage < 35) {
                            List<Object> row = table.rows().get(rowIndex++);
                            List<String> values = row.stream().map(this::pdfValue).toList();
                            y = writePdfLine(content, String.join(" | ", values), PDType1Font.HELVETICA, 8, 42, y, 528);
                            y -= 2;
                            rowsOnPage++;
                        }
                        if (table.rows().isEmpty()) {
                            y = writePdfLine(content, "No records found for this period.", PDType1Font.HELVETICA, 9, 42, y, 528);
                        }
                    }
                } while (rowIndex < table.rows().size());
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private String pdfValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        String text = String.valueOf(value).replaceAll("[^\\x20-\\x7E]", "?").replace('\n', ' ').replace('\r', ' ');
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private float writePdfLine(PDPageContentStream content, String text, PDType1Font font, float size,
                               float x, float y, float maxWidth) throws IOException {
        String safeText = text.replaceAll("[^\\x20-\\x7E]", "?");
        while (font.getStringWidth(safeText) / 1000 * size > maxWidth && safeText.length() > 1) {
            safeText = safeText.substring(0, safeText.length() - 1);
        }
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safeText);
        content.endText();
        return y - size - 4;
    }

    private MediaType contentTypeFor(String format) {
        return switch (format) {
            case "XLSX" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "PDF" -> MediaType.APPLICATION_PDF;
            default -> new MediaType("text", "csv", StandardCharsets.UTF_8);
        };
    }

    private String extensionFor(String format) {
        return switch (format) {
            case "XLSX" -> "xlsx";
            case "PDF" -> "pdf";
            default -> "csv";
        };
    }

    private java.util.Optional<Map<String, String>> findReportMetadata(String reportId) {
        DataFormatter formatter = new DataFormatter();
        return storageService.findById("Reports", "ReportID", reportId, row -> {
            Row headerRow = row.getSheet().getRow(0);
            Map<String, String> metadata = new HashMap<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                String key = formatter.formatCellValue(headerRow.getCell(column)).trim();
                Cell valueCell = row.getCell(column);
                metadata.put(key, valueCell == null ? "" : formatter.formatCellValue(valueCell).trim());
            }
            return metadata;
        });
    }

    private record ReportPeriod(LocalDate startDate, LocalDate endDate) {
    }

    private record ReportDefinition(String title, String username, String period,
                                    LocalDate startDate, LocalDate endDate, LocalDate generatedDate,
                                    List<ReportTable> tables) {
    }

    private record ReportTable(String title, List<String> headers, List<List<Object>> rows) {
    }

    public static class CategorySummary {
        private final String name;
        private String type = "INCOME";
        private BigDecimal incomeTotal = BigDecimal.ZERO;
        private BigDecimal expenseTotal = BigDecimal.ZERO;

        public CategorySummary(String name) {
            this.name = name;
        }

        public void addIncome(BigDecimal amount) {
            incomeTotal = incomeTotal.add(amountOrZeroStatic(amount));
        }

        public void addExpense(BigDecimal amount) {
            expenseTotal = expenseTotal.add(amountOrZeroStatic(amount));
            if (incomeTotal.compareTo(BigDecimal.ZERO) == 0) {
                type = "EXPENSE";
            } else {
                type = "MIXED";
            }
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public BigDecimal getIncomeTotal() { return incomeTotal; }
        public BigDecimal getExpenseTotal() { return expenseTotal; }
        public BigDecimal getNetAmount() { return incomeTotal.subtract(expenseTotal); }

        private static BigDecimal amountOrZeroStatic(BigDecimal amount) {
            return Objects.requireNonNullElse(amount, BigDecimal.ZERO);
        }
    }

    public static class BudgetSummary {
        private final String category;
        private final String budgetMonth;
        private final BigDecimal limitAmount;
        private final BigDecimal spentAmount;
        private final boolean active;
        private final BigDecimal remainingAmount;
        private final double percentageUsed;
        private final String alertLevel;

        public BudgetSummary(String category, String budgetMonth, BigDecimal limitAmount,
                             BigDecimal spentAmount, boolean active) {
            this.category = category;
            this.budgetMonth = budgetMonth;
            this.limitAmount = Objects.requireNonNullElse(limitAmount, BigDecimal.ZERO);
            this.spentAmount = Objects.requireNonNullElse(spentAmount, BigDecimal.ZERO);
            this.active = active;
            this.remainingAmount = this.limitAmount.subtract(this.spentAmount);
            this.percentageUsed = this.limitAmount.compareTo(BigDecimal.ZERO) > 0
                    ? this.spentAmount.multiply(BigDecimal.valueOf(100))
                    .divide(this.limitAmount, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            if (this.spentAmount.compareTo(this.limitAmount) > 0) {
                this.alertLevel = "OVER";
            } else if (this.percentageUsed >= 90) {
                this.alertLevel = "CRITICAL";
            } else if (this.percentageUsed >= 75) {
                this.alertLevel = "WARNING";
            } else if (this.percentageUsed >= 50) {
                this.alertLevel = "CAUTION";
            } else {
                this.alertLevel = "SAFE";
            }
        }

        public String getCategory() { return category; }
        public String getBudgetMonth() { return budgetMonth; }
        public BigDecimal getLimitAmount() { return limitAmount; }
        public BigDecimal getSpentAmount() { return spentAmount; }
        public BigDecimal getRemainingAmount() { return remainingAmount; }
        public double getPercentageUsed() { return percentageUsed; }
        public String getAlertLevel() { return alertLevel; }
        public boolean isActive() { return active; }
        public boolean isOverBudget() { return spentAmount.compareTo(limitAmount) > 0; }
    }

    public record Insight(String priority, String message, String recommendation) {
        public String getPriority() { return priority; }
        public String getMessage() { return message; }
        public String getRecommendation() { return recommendation; }
    }
}
