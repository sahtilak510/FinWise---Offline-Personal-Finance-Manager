package com.finance.chatbot.service;

import com.finance.chatbot.model.ConversationContext;
import com.finance.model.entity.*;
import com.finance.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatbotService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetRepository budgetRepository;
    private final FinancialGoalRepository financialGoalRepository;
    private final AccountRepository accountRepository;

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Set<String> SQL_BLOCKLIST = Set.of(
            "drop", "delete", "truncate", "alter", "insert", "update", "select ", "--", ";--", "union select"
    );

    private static final ConcurrentHashMap<Long, ConversationContext> contexts = new ConcurrentHashMap<>();

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\$?(\\d+\\.?\\d*)");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?:top|first|show|limit)\\s*(\\d+)");

    private static final List<String> KNOWN_CATEGORIES = List.of(
            "food", "transportation", "transport", "entertainment", "healthcare", "health",
            "shopping", "utilities", "rent", "education", "salary", "freelance",
            "investments", "gifts", "travel"
    );

    private double lastConfidence;
    private String lastIntent;
    private List<String> lastSuggestions = List.of();

    public ChatbotService(ExpenseRepository expenseRepository,
                          IncomeRepository incomeRepository,
                          BudgetRepository budgetRepository,
                          FinancialGoalRepository financialGoalRepository,
                          AccountRepository accountRepository) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.budgetRepository = budgetRepository;
        this.financialGoalRepository = financialGoalRepository;
        this.accountRepository = accountRepository;
    }

    // ──────────────────────────── Public API ────────────────────────────

    public static class ChatResult {
        public String message;
        public double confidence;
        public String intent;
        public List<String> suggestions;
        public Map<String, Object> chart;

        public ChatResult() {
            this.message = "";
            this.confidence = 0.0;
            this.intent = "UNKNOWN";
            this.suggestions = List.of();
        }

        public ChatResult(String message, double confidence, String intent, List<String> suggestions) {
            this.message = message;
            this.confidence = confidence;
            this.intent = intent;
            this.suggestions = suggestions;
            this.chart = null;
        }

        public ChatResult(String message, double confidence, String intent, List<String> suggestions, Map<String, Object> chart) {
            this.message = message;
            this.confidence = confidence;
            this.intent = intent;
            this.suggestions = suggestions;
            this.chart = chart;
        }
    }

    public ChatResult generateResponse(User user, String userMessage) {
        if (user == null) {
            return new ChatResult(
                    card("Authentication Required", "Please log in to use the chatbot."),
                    1.0, "AUTH_REQUIRED", List.of("Log in", "Go to home page")
            );
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ChatResult(
                    emptyMessage(),
                    0.0, "EMPTY", defaultSuggestions()
            );
        }
        String trimmed = userMessage.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            return new ChatResult(
                    card("⚠️ Message Too Long", "Your message is " + trimmed.length() + " characters. Please keep it under " + MAX_MESSAGE_LENGTH + " characters."),
                    0.0, "TOO_LONG", defaultSuggestions()
            );
        }
        String lowerRaw = trimmed.toLowerCase();
        for (String blocked : SQL_BLOCKLIST) {
            if (lowerRaw.contains(blocked) && lowerRaw.contains(";")) {
                return new ChatResult(
                        card("⚠️ Blocked", "I can only answer predefined financial questions using your local data."),
                        0.0, "BLOCKED", defaultSuggestions()
                );
            }
        }

        String msg = lowerRaw;

        // ── Context management ──
        ConversationContext ctx = contexts.computeIfAbsent(user.getId(), k -> new ConversationContext());
        if (ctx.isStale()) {
            ctx.clear();
        }

        // Check for follow-ups before primary intent matching
        if (!ctx.isStale() && ctx.getLastIntent() != null && isFollowUp(msg)) {
            ChatResult followUp = handleFollowUp(user, msg, ctx);
            if (followUp != null) {
                ctx.touch();
                return followUp;
            }
        }
        ctx.touch();

        // ── Clear context ──
        if (matchesAny(msg, "clear context", "reset", "start over")) {
            ctx.clear();
            return new ChatResult(
                    card("Context Cleared", "Conversation context has been reset. Start fresh!"),
                    1.0, "CLEAR_CONTEXT", defaultSuggestions()
            );
        }

        // ── Greeting ── (robust word-boundary, avoid false positive like "hi" inside "this")
        if (isGreeting(msg)) {
            return buildResult("GREETING", 1.0, greetingResponse(user), defaultSuggestions());
        }

        // ── Help ──
        if (matchesAny(msg, "help", "what can you do", "capabilities", "commands", "options", "menu")) {
            return buildResult("HELP", 1.0, helpResponse(), defaultSuggestions());
        }

        // ── Goodbye ──
        if (matchesAny(msg, "bye", "goodbye", "see you", "quit", "exit", "cya")) {
            return buildResult("GOODBYE", 1.0, "Goodbye! Your data stays safely offline. Come back anytime!",
                    List.of("Show my balance", "Monthly report"));
        }

        // ── Thanks ──
        if (matchesAny(msg, "thank", "thanks", "thx", "appreciate", "ty")) {
            return buildResult("THANKS", 1.0, "You're welcome! Anything else about your finances?",
                    List.of("Show my balance", "Recent transactions", "Budget status"));
        }

        // ── Who are you ──
        if (matchesAny(msg, "who are you", "what are you", "your name", "who is finwise")) {
            return buildResult("WHO_ARE_YOU", 1.0, whoAreYouResponse(), defaultSuggestions());
        }

        // ── Score-based intent detection ──
        IntentMatch bestMatch = detectIntent(msg);
        String intent = bestMatch.intent;
        double confidence = bestMatch.score;

        // Extract entities
        String category = extractCategory(msg);
        LocalDate[] dateRange = detectDatePeriod(msg);
        BigDecimal amount = extractAmount(msg);
        int limit = extractLimit(msg);

        // Store extracted entities in context
        if (category != null) ctx.setLastEntityCategory(category);
        if (dateRange != null) {
            ctx.setLastDateStart(dateRange[0]);
            ctx.setLastDateEnd(dateRange[1]);
        }
        if (amount != null) ctx.setLastAmount(amount);
        if (limit > 0) ctx.setLastLimit(limit);

        // ── Dispatch ──
        String response;
        List<String> suggestions;
        switch (intent) {
            case "TOTAL_BALANCE":
                response = totalBalanceResponse(user);
                suggestions = List.of("Show account details", "Income vs expenses", "Net worth history");
                break;
            case "TOTAL_EXPENSES":
                response = totalExpensesResponse(user, dateRange);
                suggestions = List.of("Spending by category", "Biggest expenses", "Budget status");
                break;
            case "EXPENSES_THIS_MONTH":
                LocalDate[] thisMonth = currentMonthRange();
                response = totalExpensesResponse(user, thisMonth);
                suggestions = List.of("Budget status", "Spending by category", "Show income");
                break;
            case "EXPENSES_LAST_MONTH":
                LocalDate[] lastMonth = lastMonthRange();
                response = totalExpensesResponse(user, lastMonth);
                suggestions = List.of("This month expenses", "Spending trends", "Monthly report");
                break;
            case "EXPENSES_BY_CATEGORY":
                response = expensesByCategoryResponse(user, dateRange);
                suggestions = List.of("Budget status", "Biggest expenses", "Spending trends");
                break;
            case "EXPENSES_BY_DATE":
                response = totalExpensesResponse(user, dateRange);
                suggestions = List.of("Income for same period", "Spending by category", "Monthly report");
                break;
            case "TOTAL_INCOME":
                response = totalIncomeResponse(user, dateRange);
                suggestions = List.of("Income by category", "Income vs expenses", "Show expenses");
                break;
            case "INCOME_THIS_MONTH":
                LocalDate[] thisMonthI = currentMonthRange();
                response = totalIncomeResponse(user, thisMonthI);
                suggestions = List.of("Show expenses", "Net balance", "Income by category");
                break;
            case "INCOME_LAST_MONTH":
                LocalDate[] lastMonthI = lastMonthRange();
                response = totalIncomeResponse(user, lastMonthI);
                suggestions = List.of("This month income", "Income vs expenses", "Monthly report");
                break;
            case "INCOME_BY_CATEGORY":
                response = incomeByCategoryResponse(user);
                suggestions = List.of("Total income", "Income vs expenses", "Monthly report");
                break;
            case "BUDGET_STATUS":
                response = budgetStatusResponse(user);
                suggestions = List.of("Budget remaining", "Spending by category", "Set new budget");
                break;
            case "BUDGET_REMAINING":
                response = budgetRemainingResponse(user);
                suggestions = List.of("Budget status", "Show expenses", "Spending by category");
                break;
            case "BUDGET_SPECIFIC":
                response = specificBudgetResponse(user, category);
                suggestions = List.of("All budgets", "Budget remaining", "Spending by category");
                break;
            case "RECENT_TRANSACTIONS":
                response = recentTransactionsResponse(user, limit > 0 ? limit : 8);
                suggestions = List.of("Biggest expenses", "Spending by category", "Monthly report");
                break;
            case "BIGGEST_EXPENSES":
                response = biggestExpensesResponse(user, limit > 0 ? limit : 5, dateRange);
                suggestions = List.of("Smallest expenses", "Spending by category", "Average expense");
                break;
            case "SMALLEST_EXPENSES":
                response = smallestExpensesResponse(user, limit > 0 ? limit : 5);
                suggestions = List.of("Biggest expenses", "Average expense", "Spending by category");
                break;
            case "AVERAGE_EXPENSE":
                response = averageExpenseResponse(user, dateRange);
                suggestions = List.of("Biggest expenses", "Spending by category", "Total expenses");
                break;
            case "INCOME_VS_EXPENSE":
                response = incomeVsExpenseResponse(user, dateRange);
                suggestions = List.of("Total income", "Total expenses", "Monthly report");
                break;
            case "GOALS_PROGRESS":
                response = goalsProgressResponse(user);
                suggestions = List.of("Account balances", "Monthly report", "Budget status");
                break;
            case "MONTHLY_REPORT":
                LocalDate[] reportRange = dateRange != null ? dateRange : currentMonthRange();
                response = monthlyReportResponse(user, reportRange);
                suggestions = List.of("Spending by category", "Budget status", "Income vs expenses");
                break;
            case "SPENDING_TRENDS":
                response = spendingTrendsResponse(user);
                suggestions = List.of("Monthly report", "Spending by category", "Biggest expenses");
                break;
            case "TRANSACTION_SEARCH":
                response = transactionSearchResponse(user, msg);
                suggestions = List.of("Recent transactions", "Spending by category", "Biggest expenses");
                break;
            case "COUNT_RECORDS":
                response = countRecordsResponse(user, msg);
                suggestions = List.of("Show expenses", "Show income", "Budget status");
                break;
            case "ACCOUNTS":
                response = accountsResponse(user);
                suggestions = List.of("Total balance", "Goals progress", "Monthly report");
                break;
            case "USER_PROFILE":
                response = userProfileResponse(user);
                suggestions = List.of("Show accounts", "Total balance", "Help");
                break;
            case "RECURRING_SPENDING":
                response = recurringSpendingResponse(user);
                suggestions = List.of("Spending by category", "Budget status", "Biggest expenses");
                break;
            case "INSIGHTS":
                response = insightsResponse(user);
                suggestions = List.of("Monthly report", "Spending trends", "Budget status");
                break;
            default:
                response = fallbackResponse();
                suggestions = defaultSuggestions();
                confidence = Math.max(confidence, 0.1);
                break;
        }

        // Update context
        ctx.setLastIntent(intent);
        ctx.touch();

        // Attach chart for useful intents with real data
        Map<String, Object> chart = null;
        if (intent.equals("EXPENSES_BY_CATEGORY") || intent.equals("INCOME_BY_CATEGORY") || intent.equals("SPENDING_TRENDS") || intent.equals("MONTHLY_REPORT") || intent.equals("INCOME_VS_EXPENSE")) {
            chart = generateChartForIntent(user, intent, dateRange);
        }

        return new ChatResult(response, confidence, intent, suggestions, chart);
    }

    public double getConfidence() { return lastConfidence; }
    public String getIntent() { return lastIntent; }
    public List<String> getSuggestions() { return lastSuggestions; }

    public void clearContext(User user) {
        if (user != null) {
            ConversationContext ctx = contexts.get(user.getId());
            if (ctx != null) ctx.clear();
        }
    }

    // ──────────────────────────── Intent Detection ────────────────────────────

    private static class IntentMatch {
        String intent;
        double score;
        IntentMatch(String intent, double score) { this.intent = intent; this.score = score; }
    }

    private IntentMatch detectIntent(String msg) {
        Map<String, Double> scores = new LinkedHashMap<>();

        // 1. TOTAL_BALANCE
        double balScore = keywordScore(msg, 0.95, "show balance", "what is my balance", "what's my balance", "balance", "net balance", "net worth", "how much do i have", "total assets", "current balance");
        scores.put("TOTAL_BALANCE", balScore);

        // 2. TOTAL_EXPENSES
        double totExpScore = keywordScore(msg, 0.9, "total expense", "total spending", "how much spent", "expenses total", "total cost",
                "how much did i spend", "how much did i spent", "show expenses", "show my expenses", "my expenses", "what is my expense", "how much expense", "did i spend", "have i spent", "show expense");
        // also treat generic "how much did i spend" without month as total
        if ((msg.contains("spend") || msg.contains("expense") || msg.contains("spent")) && !msg.contains("this month") && !msg.contains("last month") && !msg.contains("category") && !msg.contains("analyze") && !msg.contains("analyse")) {
            totExpScore = Math.max(totExpScore, 0.6);
        }
        scores.put("TOTAL_EXPENSES", totExpScore);

        // 3. EXPENSES_THIS_MONTH
        double expTmScore = keywordScore(msg, 0.95, "spend this month", "expenses this month", "how much did i spend this month",
                "spending this month", "expense this month", "this month spend", "this month spent");
        if (msg.contains("this month") && (msg.contains("spend") || msg.contains("expense") || msg.contains("spent"))) {
            expTmScore = Math.max(expTmScore, 0.95);
        }
        scores.put("EXPENSES_THIS_MONTH", expTmScore);

        // 4. EXPENSES_LAST_MONTH
        double expLmScore = keywordScore(msg, 0.95, "spend last month", "expenses last month", "spending last month", "expense last month");
        if (msg.contains("last month") && (msg.contains("spend") || msg.contains("expense") || msg.contains("spent"))) {
            expLmScore = Math.max(expLmScore, 0.95);
        }
        scores.put("EXPENSES_LAST_MONTH", expLmScore);

        // 5. EXPENSES_BY_CATEGORY  — also handles "Analyze Expenses"
        double catScore = keywordScore(msg, 0.95, "analyze expenses", "analyse expenses", "analyse my expenses", "analyze my expenses",
                "expense analysis", "expense summary", "spending by category", "category breakdown", "where did i spend",
                "spend by category", "expenses by category", "expense by category", "breakdown by category", "spending breakdown");
        if (extractCategory(msg) != null && (msg.contains("expense") || msg.contains("spend"))) {
            catScore = Math.max(catScore, 0.85);
        }
        if (msg.equals("analyze expenses") || msg.equals("show expenses breakdown")) catScore = 0.95;
        scores.put("EXPENSES_BY_CATEGORY", catScore);

        // 6. EXPENSES_BY_DATE
        double expDateScore = 0.0;
        if (containsMonthName(msg) || msg.contains("this year") || msg.contains("last year") || msg.contains("this week") || msg.contains("last week")) {
            if (msg.contains("expense") || msg.contains("spend") || msg.contains("spent") || msg.contains("cost")) {
                expDateScore = 0.9;
            }
        }
        scores.put("EXPENSES_BY_DATE", expDateScore);

        // 7. TOTAL_INCOME
        double totIncScore = keywordScore(msg, 0.9, "total income", "how much earned", "income total", "total revenue",
                "how much income", "how much income do i have", "my income", "show income", "what is my income", "what's my income", "whats my income", "income do i have", "show my income");
        if (msg.contains("income") && !msg.contains("this month") && !msg.contains("last month") && !msg.contains("category")) {
            totIncScore = Math.max(totIncScore, 0.55);
        }
        scores.put("TOTAL_INCOME", totIncScore);

        // 8. INCOME_THIS_MONTH
        double incTmScore = keywordScore(msg, 0.95, "monthly income", "income this month", "what is my monthly income",
                "what's my monthly income", "whats my monthly income", "this month income", "income for this month");
        if (msg.contains("this month") && msg.contains("income")) {
            incTmScore = Math.max(incTmScore, 0.95);
        }
        if (msg.contains("monthly income")) incTmScore = Math.max(incTmScore, 0.96);
        scores.put("INCOME_THIS_MONTH", incTmScore);

        // 9. INCOME_LAST_MONTH
        double incLmScore = keywordScore(msg, 0.95, "income last month", "earnings last month");
        if (msg.contains("last month") && msg.contains("income")) {
            incLmScore = Math.max(incLmScore, 0.95);
        }
        scores.put("INCOME_LAST_MONTH", incLmScore);

        // 10. INCOME_BY_CATEGORY
        double incCatScore = keywordScore(msg, 0.9, "income by category", "income breakdown", "income sources",
                "where did i earn", "earnings by category");
        scores.put("INCOME_BY_CATEGORY", incCatScore);

        // 11. BUDGET_STATUS — also handles "Budget Summary"
        double budgStatScore = keywordScore(msg, 0.95, "budget summary", "give me a budget summary", "budget status", "budget overview", "all budgets",
                "show budget", "show my budget", "budgets", "within budget", "over budget", "under budget", "budget limit", "budget summary");
        if (msg.contains("budget") && (msg.contains("summary") || msg.contains("overview") || msg.contains("status"))) budgStatScore = Math.max(budgStatScore, 0.95);
        if (msg.equals("budget summary") || msg.equals("show budget summary")) budgStatScore = 0.96;
        scores.put("BUDGET_STATUS", budgStatScore);

        // 12. BUDGET_REMAINING
        double budgRemScore = keywordScore(msg, 0.95, "budget remaining", "budget left", "remaining budget",
                "how much budget is remaining", "how much budget left", "budget leftover", "budget is remaining");
        scores.put("BUDGET_REMAINING", budgRemScore);

        // 13. BUDGET_SPECIFIC
        double budgSpecScore = 0.0;
        if (extractCategory(msg) != null && msg.contains("budget")) {
            budgSpecScore = 0.9;
        }
        scores.put("BUDGET_SPECIFIC", budgSpecScore);

        // 14. RECENT_TRANSACTIONS
        double recentScore = keywordScore(msg, 0.9, "recent transactions", "show transactions", "latest transactions",
                "recent expenses", "recent spending", "latest expense", "last expense", "my transactions",
                "transaction history", "all transactions");
        scores.put("RECENT_TRANSACTIONS", recentScore);

        // 15. BIGGEST_EXPENSES
        double bigScore = keywordScore(msg, 0.95, "biggest expenses", "biggest expense", "largest expense",
                "largest expenses", "top expenses", "highest expense", "most expensive", "biggest spending");
        scores.put("BIGGEST_EXPENSES", bigScore);

        // 16. SMALLEST_EXPENSES
        double smallScore = keywordScore(msg, 0.95, "smallest expense", "smallest expenses", "least expense",
                "lowest expense", "cheapest", "least expensive", "lowest spending");
        scores.put("SMALLEST_EXPENSES", smallScore);

        // 17. AVERAGE_EXPENSE
        double avgScore = keywordScore(msg, 0.9, "average expense", "average spending", "mean expense",
                "mean spending", "typical expense", "average cost");
        scores.put("AVERAGE_EXPENSE", avgScore);

        // 18. INCOME_VS_EXPENSE
        double vsScore = keywordScore(msg, 0.95, "income vs expense", "income vs expenses", "compare income",
                "income versus expense", "income versus expenses", "income and expense", "income compared to expense",
                "earnings vs spending", "money in vs money out");
        scores.put("INCOME_VS_EXPENSE", vsScore);

        // 19. GOALS_PROGRESS
        double goalScore = keywordScore(msg, 0.9, "goals", "savings goal", "financial goal", "goal progress",
                "my goals", "goal status", "target progress", "savings progress");
        scores.put("GOALS_PROGRESS", goalScore);

        // 20. MONTHLY_REPORT
        double reportScore = keywordScore(msg, 0.95, "monthly report", "monthly summary", "financial report",
                "report for this month", "this month report", "monthly overview");
        if (msg.contains("report") || msg.contains("summary")) {
            reportScore = Math.max(reportScore, 0.85);
        }
        scores.put("MONTHLY_REPORT", reportScore);

        // 21. SPENDING_TRENDS
        double trendScore = keywordScore(msg, 0.9, "spending trend", "spending pattern", "am i spending more",
                "spending habits", "spending behavior", "trend", "trending", "spending over time");
        scores.put("SPENDING_TRENDS", trendScore);

        // 22. TRANSACTION_SEARCH
        double searchScore = keywordScore(msg, 0.85, "search transactions", "find transactions", "transactions with",
                "search expenses", "find expenses", "look for transactions");
        scores.put("TRANSACTION_SEARCH", searchScore);

        // 23. COUNT_RECORDS
        double countScore = keywordScore(msg, 0.92, "how many expenses", "count expenses", "number of expenses",
                "how many income", "count income", "number of income", "how many records",
                "how many goals", "how many budgets", "how many accounts", "number of goals", "number of budgets",
                "number of", "how many", "count");
        if (msg.contains("how many") && (msg.contains("goal") || msg.contains("budget") || msg.contains("expense") || msg.contains("income"))) {
            countScore = Math.max(countScore, 0.92);
        }
        scores.put("COUNT_RECORDS", countScore);

        // 24. ACCOUNTS
        double acctScore = keywordScore(msg, 0.9, "accounts", "account balances", "account balance",
                "my accounts", "show accounts", "bank accounts", "wallet balance", "account list");
        scores.put("ACCOUNTS", acctScore);

        // 25. USER_PROFILE
        double profileScore = keywordScore(msg, 0.85, "my profile", "my info", "user info",
                "account info", "user profile", "my details");
        scores.put("USER_PROFILE", profileScore);

        // 26. RECURRING_SPENDING
        double recurScore = keywordScore(msg, 0.85, "recurring expenses", "recurring spending", "subscriptions",
                "recurring payments", "monthly subscriptions", "recurring charges");
        scores.put("RECURRING_SPENDING", recurScore);

        // 27. INSIGHTS
        double insightScore = keywordScore(msg, 0.85, "insight", "advice", "recommendation", "tips",
                "suggest", "suggestion", "financial advice", "money tips");
        scores.put("INSIGHTS", insightScore);

        // Find the best
        String bestIntent = "UNKNOWN";
        double bestScore = 0.0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestIntent = e.getKey();
            }
        }

        // Penalize very low scores
        if (bestScore < 0.3) {
            bestIntent = "UNKNOWN";
            bestScore = Math.max(bestScore, 0.1);
        }

        // Boost confidence for extracted entities
        if (extractCategory(msg) != null) bestScore = Math.min(bestScore + 0.05, 1.0);
        if (detectDatePeriod(msg) != null && !isDefaultDateRange(detectDatePeriod(msg))) bestScore = Math.min(bestScore + 0.05, 1.0);

        return new IntentMatch(bestIntent, Math.round(bestScore * 100.0) / 100.0);
    }

    private double keywordScore(String msg, double baseScore, String... keywords) {
        double score = 0.0;
        for (String kw : keywords) {
            if (msg.contains(kw)) {
                score = Math.max(score, baseScore);
            }
        }
        // Partial keyword match boost
        for (String kw : keywords) {
            String[] parts = kw.split("\\s+");
            long matched = Arrays.stream(parts).filter(msg::contains).count();
            if (matched > 0 && matched < parts.length) {
                double partial = baseScore * 0.5 * ((double) matched / parts.length);
                score = Math.max(score, partial);
            }
        }
        return score;
    }

    // ──────────────────────────── Entity Extraction ────────────────────────────

    private LocalDate[] detectDatePeriod(String msg) {
        YearMonth now = YearMonth.now();
        if (msg.contains("this month") || msg.contains("current month")) {
            return new LocalDate[]{now.atDay(1), now.atEndOfMonth()};
        }
        if (msg.contains("last month") || msg.contains("previous month")) {
            YearMonth lm = now.minusMonths(1);
            return new LocalDate[]{lm.atDay(1), lm.atEndOfMonth()};
        }
        if (msg.contains("this year")) {
            return new LocalDate[]{LocalDate.of(now.getYear(), 1, 1), LocalDate.of(now.getYear(), 12, 31)};
        }
        if (msg.contains("last year")) {
            int y = now.getYear() - 1;
            return new LocalDate[]{LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)};
        }
        if (msg.contains("this week")) {
            LocalDate today = LocalDate.now();
            return new LocalDate[]{today.with(DayOfWeek.MONDAY), today};
        }
        if (msg.contains("last week")) {
            LocalDate today = LocalDate.now();
            return new LocalDate[]{today.minusWeeks(1).with(DayOfWeek.MONDAY), today.minusWeeks(1).with(DayOfWeek.SUNDAY)};
        }
        // Month name detection
        String[] months = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"};
        for (int i = 0; i < months.length; i++) {
            if (msg.contains(months[i])) {
                YearMonth ym = YearMonth.of(now.getYear(), i + 1);
                return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
        }
        // Quarter detection
        if (msg.contains("q1")) return new LocalDate[]{LocalDate.of(now.getYear(), 1, 1), LocalDate.of(now.getYear(), 3, 31)};
        if (msg.contains("q2")) return new LocalDate[]{LocalDate.of(now.getYear(), 4, 1), LocalDate.of(now.getYear(), 6, 30)};
        if (msg.contains("q3")) return new LocalDate[]{LocalDate.of(now.getYear(), 7, 1), LocalDate.of(now.getYear(), 9, 30)};
        if (msg.contains("q4")) return new LocalDate[]{LocalDate.of(now.getYear(), 10, 1), LocalDate.of(now.getYear(), 12, 31)};
        // Default: all time
        return new LocalDate[]{LocalDate.of(2000, 1, 1), LocalDate.now()};
    }

    private boolean isDefaultDateRange(LocalDate[] range) {
        return range[0].getYear() == 2000;
    }

    private LocalDate[] currentMonthRange() {
        YearMonth now = YearMonth.now();
        return new LocalDate[]{now.atDay(1), now.atEndOfMonth()};
    }

    private LocalDate[] lastMonthRange() {
        YearMonth lm = YearMonth.now().minusMonths(1);
        return new LocalDate[]{lm.atDay(1), lm.atEndOfMonth()};
    }

    private String extractCategory(String msg) {
        for (String cat : KNOWN_CATEGORIES) {
            if (msg.contains(cat)) {
                // Normalize synonyms
                if ("transport".equals(cat)) return "Transportation";
                if ("health".equals(cat)) return "Healthcare";
                return capitalize(cat);
            }
        }
        return null;
    }

    private BigDecimal extractAmount(String msg) {
        Matcher m = AMOUNT_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private int extractLimit(String msg) {
        Matcher m = LIMIT_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private boolean containsMonthName(String msg) {
        String[] months = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december",
                "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (String m : months) {
            if (msg.contains(m)) return true;
        }
        return false;
    }

    // ──────────────────────────── Follow-up Handling ────────────────────────────

    private boolean isGreeting(String msg) {
        String t = msg.trim().toLowerCase();
        // Finance keywords indicate not a pure greeting even if "hi" substring appears (e.g., "this")
        boolean containsFinance = t.contains("expense") || t.contains("spend") || t.contains("spent") || t.contains("income") || t.contains("balance") || t.contains("budget") || t.contains("transaction") || t.contains("goal") || t.contains("account");
        if (containsFinance) return false;
        // Word-boundary greeting detection
        if (t.matches(".*\\b(hello|hi|hey|greetings|howdy)\\b.*")) {
            // Ensure it's short or greeting at start
            if (t.length() < 30) return true;
            if (t.matches("^(hello|hi|hey|greetings|howdy|good morning|good evening|good afternoon)\\b.*") ) return true;
        }
        if (t.contains("good morning") || t.contains("good evening") || t.contains("good afternoon")) {
            if (t.length() < 40) return true;
        }
        return false;
    }

    private boolean isFollowUp(String msg) {
        String t = msg.trim().toLowerCase();
        // Full finance questions are NOT follow-ups — they are new intents
        if (t.contains("how much") || t.contains("what is") || t.contains("what are") || t.contains("show ") || t.contains("give me") || t.contains("analyze") || t.contains("analyse") || t.contains("budget summary") || t.contains("recent transactions")) {
            if (t.length() > 12) return false;
        }
        // Very short ambiguous messages (e.g., "last month", "top 5") are follow-ups
        if (t.length() < 12 && !t.contains("?") && !t.contains("expense") && !t.contains("income") && !t.contains("budget") && !t.contains("balance") && !t.contains("transaction")) {
            return true;
        }
        String[] followUpStarters = {"what about", "how about", "and ", "also", "show me", "tell me about", "what else"};
        for (String p : followUpStarters) {
            if (t.startsWith(p) && t.length() < 26) return true;
        }
        // Pure date period short — only if msg is mostly just the date, not a full finance question like "this month spend"
        if (t.matches(".*\\b(last month|this month|this year|last year|this week|last week|january|february|march|april|may|june|july|august|september|october|november|december)\\b.*") && t.length() < 18) {
            if (!t.contains("spend") && !t.contains("expense") && !t.contains("spent") && !t.contains("income") && !t.contains("balance") && !t.contains("budget") && !t.contains("transaction") && !t.contains("spending")) {
                return true;
            }
        }
        // Pure number/limit short
        if (t.matches(".*\\b(top\\s*\\d+|first\\s*\\d+|show\\s*\\d+)\\b.*") && t.length() < 18) {
            return true;
        }
        return false;
    }

    private ChatResult handleFollowUp(User user, String msg, ConversationContext ctx) {
        String lastIntent = ctx.getLastIntent();
        if (lastIntent == null) return null;

        // Detect new entities from follow-up
        LocalDate[] newDate = detectDatePeriod(msg);
        String newCategory = extractCategory(msg);
        int newLimit = extractLimit(msg);

        // Override context with new entities
        if (newDate != null && !isDefaultDateRange(newDate)) {
            ctx.setLastDateStart(newDate[0]);
            ctx.setLastDateEnd(newDate[1]);
        }
        if (newCategory != null) {
            ctx.setLastEntityCategory(newCategory);
        }
        if (newLimit > 0) {
            ctx.setLastLimit(newLimit);
        }

        LocalDate[] range = (ctx.getLastDateStart() != null && ctx.getLastDateEnd() != null)
                ? new LocalDate[]{ctx.getLastDateStart(), ctx.getLastDateEnd()}
                : null;
        int lim = ctx.getLastLimit() > 0 ? ctx.getLastLimit() : 5;

        switch (lastIntent) {
            case "EXPENSES_THIS_MONTH":
            case "EXPENSES_LAST_MONTH":
            case "EXPENSES_BY_DATE":
            case "TOTAL_EXPENSES":
                if (newDate != null && !isDefaultDateRange(newDate)) {
                    return buildResult("EXPENSES_BY_DATE", 0.9, totalExpensesResponse(user, newDate),
                            List.of("Spending by category", "Income for same period", "Monthly report"));
                }
                break;
            case "INCOME_THIS_MONTH":
            case "INCOME_LAST_MONTH":
            case "TOTAL_INCOME":
                if (newDate != null && !isDefaultDateRange(newDate)) {
                    return buildResult("TOTAL_INCOME", 0.9, totalIncomeResponse(user, newDate),
                            List.of("Income by category", "Income vs expenses", "Show expenses"));
                }
                break;
            case "BIGGEST_EXPENSES":
            case "SMALLEST_EXPENSES":
                if (newLimit > 0) {
                    return buildResult("BIGGEST_EXPENSES", 0.9,
                            biggestExpensesResponse(user, lim, range),
                            List.of("Smallest expenses", "Spending by category", "Average expense"));
                }
                break;
            case "EXPENSES_BY_CATEGORY":
                if (newCategory != null) {
                    return buildResult("EXPENSES_BY_CATEGORY", 0.9,
                            expensesByCategoryResponse(user, range),
                            List.of("Budget status", "Biggest expenses", "Total expenses"));
                }
                break;
            case "BUDGET_STATUS":
            case "BUDGET_REMAINING":
                if (newCategory != null) {
                    return buildResult("BUDGET_SPECIFIC", 0.9,
                            specificBudgetResponse(user, newCategory),
                            List.of("All budgets", "Budget remaining", "Spending by category"));
                }
                break;
            case "MONTHLY_REPORT":
                if (newDate != null) {
                    return buildResult("MONTHLY_REPORT", 0.9,
                            monthlyReportResponse(user, newDate),
                            List.of("Spending by category", "Budget status", "Income vs expenses"));
                }
                break;
            default:
                break;
        }
        return null;
    }

    // ──────────────────────────── Response Builders (HTML) ────────────────────────────

    private String totalBalanceResponse(User user) {
        BigDecimal totalBalance = accountRepository.getTotalBalance(user);
        totalBalance = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        List<Account> accounts = accountRepository.findByUserAndIsActiveTrueOrderByAccountNameAsc(user);

        BigDecimal totalIncome = incomeRepository.getTotalIncomeByDateRange(user, LocalDate.of(2000, 1, 1), LocalDate.now());
        BigDecimal totalExpenses = expenseRepository.getTotalExpensesByDateRange(user, LocalDate.of(2000, 1, 1), LocalDate.now());
        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
        BigDecimal netWorth = totalIncome.subtract(totalExpenses);

        StringBuilder sb = new StringBuilder();
        sb.append(card("Total Balance", statRow("Account Balance", formatCurrency(totalBalance)) + statRow("Net Worth (All Time)", formatCurrency(netWorth)) + statRow("Total Income", formatCurrency(totalIncome)) + statRow("Total Expenses", formatCurrency(totalExpenses))));
        if (!accounts.isEmpty()) {
            sb.append("<div style='margin-top:8px'>");
            for (Account a : accounts) {
                sb.append("<div class=\"fw-chat-card\" style=\"margin-bottom:4px;padding:6px 10px;\">");
                sb.append("<span>").append(escHtml(a.getAccountName())).append(" (").append(escHtml(a.getAccountType())).append(")</span> — <strong>").append(formatCurrency(a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)).append("</strong>");
                if (a.getInstitution() != null) sb.append(" <span style='color:#888'>@ ").append(escHtml(a.getInstitution())).append("</span>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    private String totalExpensesResponse(User user, LocalDate[] range) {
        if (range == null) range = new LocalDate[]{LocalDate.of(2000, 1, 1), LocalDate.now()};
        BigDecimal total = expenseRepository.getTotalExpensesByDateRange(user, range[0], range[1]);
        total = total != null ? total : BigDecimal.ZERO;
        List<Expense> expenses = expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]);
        String period = describePeriod(range);

        if (expenses.isEmpty()) {
            return card("Expenses " + period,
                    "<div class=\"fw-chat-stat\"><div class=\"fw-chat-stat-value\">₹0.00</div><div class=\"fw-chat-stat-label\">No expenses recorded</div></div>" +
                    "<p style='color:#888;margin-top:8px'>No expenses found for this period. Add expenses to see totals.</p>");
        }

        Map<String, BigDecimal> cats = new LinkedHashMap<>();
        for (Expense e : expenses) cats.merge(e.getCategory(), expenseAmount(e), (left, right) -> addAmounts(left, right));

        String topCat = cats.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + formatCurrency(e.getValue()) + ")").orElse("N/A");

        String content = statRow("Total Expenses", formatCurrency(total)) +
                statRow("Number of Transactions", String.valueOf(expenses.size())) +
                statRow("Top Category", topCat) +
                statRow("Period", period);

        return card("Expenses " + period, content);
    }

    private String expensesByCategoryResponse(User user, LocalDate[] range) {
        List<Expense> expenses;
        if (range != null && !isDefaultDateRange(range)) {
            expenses = expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]);
        } else {
            expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        }
        if (expenses.isEmpty()) {
            return card("Spending by Category", "<p style='color:#888'>You don't have any expenses recorded yet.</p>");
        }

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Expense e : expenses) totals.merge(e.getCategory(), expenseAmount(e), (left, right) -> addAmounts(left, right));
        BigDecimal grandTotal = totals.values().stream().reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        String period = (range != null && !isDefaultDateRange(range)) ? describePeriod(range) : "All Time";

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Category", "Amount", "Share"));
        totals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> {
                    BigDecimal pct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().multiply(BigDecimal.valueOf(100)).divide(grandTotal, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    table.append(tableRow(e.getKey(), formatCurrency(e.getValue()), pct + "%"));
                });

        StringBuilder sb = new StringBuilder();
        sb.append(card("Spending by Category (" + period + ")", table.toString()));
        sb.append("<div style='margin-top:8px'>");
        totals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> {
                    BigDecimal pct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().multiply(BigDecimal.valueOf(100)).divide(grandTotal, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    sb.append(progressItem(e.getKey(), pct.intValue()));
                });
        sb.append("</div>");
        sb.append(card("Total", statRow("Grand Total", formatCurrency(grandTotal)) + statRow("Total Expenses", formatCurrency(grandTotal)) + statRow("Number of transactions", String.valueOf(expenses.size()))));
        // Spec example sentence for expense analysis
        Map.Entry<String, BigDecimal> topEntry = totals.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (topEntry != null && grandTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal topPct = topEntry.getValue().multiply(BigDecimal.valueOf(100)).divide(grandTotal, 0, RoundingMode.HALF_UP);
            sb.append("<p style='margin-top:8px;color:var(--surface-300);font-size:13px'>Largest category: " + escHtml(topEntry.getKey()) + " (" + formatCurrency(topEntry.getValue()) + ")<br>Top spending category: " + escHtml(topEntry.getKey()) + "<br>You spent approximately " + topPct + "% of your expenses on " + escHtml(topEntry.getKey()) + ".</p>");
        }
        return sb.toString();
    }

    private String totalIncomeResponse(User user, LocalDate[] range) {
        if (range == null) range = new LocalDate[]{LocalDate.of(2000, 1, 1), LocalDate.now()};
        BigDecimal total = incomeRepository.getTotalIncomeByDateRange(user, range[0], range[1]);
        total = total != null ? total : BigDecimal.ZERO;
        List<Income> incomes = incomeRepository.findByUserAndIncomeDateBetween(user, range[0], range[1]);
        String period = describePeriod(range);

        if (incomes.isEmpty()) {
            return card("Income " + period,
                    "<div class=\"fw-chat-stat\"><div class=\"fw-chat-stat-value\">₹0.00</div><div class=\"fw-chat-stat-label\">No income recorded</div></div>" +
                    "<p style='color:#888;margin-top:8px'>No income found for this period.</p>");
        }

        BigDecimal avg = total.divide(BigDecimal.valueOf(incomes.size()), 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> cats = new LinkedHashMap<>();
        for (Income i : incomes) cats.merge(i.getCategory(), incomeAmount(i), (left, right) -> addAmounts(left, right));

        String content = statRow("Total Income", formatCurrency(total)) +
                statRow("Number of Entries", String.valueOf(incomes.size())) +
                statRow("Average per Entry", formatCurrency(avg)) +
                statRow("Period", period);

        if (cats.size() > 1) {
            content += "<div style='margin-top:8px'>";
            for (Map.Entry<String, BigDecimal> e : cats.entrySet()) {
                content += "<span class=\"fw-chat-badge fw-chat-badge-success\" style=\"margin:2px\">" + escHtml(e.getKey()) + ": " + formatCurrency(e.getValue()) + "</span>";
            }
            content += "</div>";
        }

        return card("Income " + period, content);
    }

    private String incomeByCategoryResponse(User user) {
        List<Income> incomes = incomeRepository.findByUserOrderByIncomeDateDesc(user);
        if (incomes.isEmpty()) {
            return card("Income by Category", "<p style='color:#888'>No income data found. Add income to see a category breakdown.</p>");
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Income i : incomes) totals.merge(i.getCategory(), incomeAmount(i), (left, right) -> addAmounts(left, right));
        BigDecimal grandTotal = totals.values().stream().reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Category", "Amount", "Share"));
        totals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> {
                    BigDecimal pct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().multiply(BigDecimal.valueOf(100)).divide(grandTotal, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    table.append(tableRow(e.getKey(), formatCurrency(e.getValue()), pct + "%"));
                });

        return card("Income by Category", table.toString() + card("Total", statRow("Grand Total", formatCurrency(grandTotal))));
    }

    private String budgetStatusResponse(User user) {
        List<Budget> budgets = budgetRepository.findByUserAndIsActiveTrue(user);
        if (budgets.isEmpty()) {
            return card("Budget Status", "<p style='color:#888'>No active budgets found. Create one to track spending limits.</p>");
        }

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Category", "Spent", "Limit", "Remaining", "Status"));

        BigDecimal totalLimit = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;

        for (Budget b : budgets) {
            BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
            BigDecimal limit = b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO;
            BigDecimal remaining = limit.subtract(spent);
            totalLimit = totalLimit.add(limit);
            totalSpent = totalSpent.add(spent);

            BigDecimal pct = limit.compareTo(BigDecimal.ZERO) > 0
                    ? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String statusBadge;
            if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
                statusBadge = badge("OVER", "danger");
            } else if (pct.compareTo(BigDecimal.valueOf(80)) >= 0) {
                statusBadge = badge("WARNING", "warning");
            } else {
                statusBadge = badge("OK", "success");
            }

            table.append(tableRow(
                    escHtml(b.getCategory()),
                    formatCurrency(spent),
                    formatCurrency(limit),
                    formatCurrency(remaining),
                    statusBadge
            ));
        }

        BigDecimal totalRemaining = totalLimit.subtract(totalSpent);
        String overallStatus = totalRemaining.compareTo(BigDecimal.ZERO) < 0 ? badge("OVER BUDGET", "danger") : badge("WITHIN BUDGET", "success");

        StringBuilder sb = new StringBuilder();
        sb.append(card("Budget Status", table.toString()));
        sb.append("<div style='margin-top:8px'>");
        for (Budget b : budgets) {
            BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
            BigDecimal limit = b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO;
            int pct = limit.compareTo(BigDecimal.ZERO) > 0
                    ? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            sb.append(progressItem(escHtml(b.getCategory()) + " (" + pct + "%)", Math.min(pct, 100)));
        }
        sb.append("</div>");
        sb.append(card("Summary", statRow("Total Limit", formatCurrency(totalLimit)) + statRow("Total Spent", formatCurrency(totalSpent)) + statRow("Total Remaining", formatCurrency(totalRemaining)) + overallStatus));
        return sb.toString();
    }

    private String budgetRemainingResponse(User user) {
        List<Budget> budgets = budgetRepository.findByUserAndIsActiveTrue(user);
        if (budgets.isEmpty()) {
            return card("Budget Remaining", "<p style='color:#888'>No active budgets. Create one to track remaining limits.</p>");
        }

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Category", "Limit", "Spent", "Remaining"));

        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (Budget b : budgets) {
            BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
            BigDecimal limit = b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO;
            BigDecimal remaining = limit.subtract(spent);
            totalRemaining = totalRemaining.add(remaining);

            String remStr = remaining.compareTo(BigDecimal.ZERO) < 0
                    ? "<span style='color:#e74c3c'>-" + formatCurrency(remaining.abs()) + "</span>"
                    : "<span style='color:#27ae60'>" + formatCurrency(remaining) + "</span>";

            table.append(tableRow(escHtml(b.getCategory()), formatCurrency(limit), formatCurrency(spent), remStr));
        }

        return card("Budget Remaining", table.toString() + card("Total Remaining", statRow("Across All Budgets", formatCurrency(totalRemaining))));
    }

    private String specificBudgetResponse(User user, String category) {
        if (category == null) {
            return budgetStatusResponse(user);
        }
        List<Budget> budgets = budgetRepository.findByUserAndIsActiveTrue(user);
        Optional<Budget> match = budgets.stream().filter(b -> b.getCategory().equalsIgnoreCase(category)).findFirst();
        if (match.isEmpty()) {
            return card("Budget: " + capitalize(category),
                    "<p style='color:#888'>No active budget found for <strong>" + escHtml(category) + "</strong>.</p>" +
                    "<p>Create one or check other categories.</p>");
        }
        Budget b = match.get();
        BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
        BigDecimal limit = b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO;
        BigDecimal remaining = limit.subtract(spent);
        int pct = limit.compareTo(BigDecimal.ZERO) > 0
                ? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP).intValue()
                : 0;
        String statusBadge = pct >= 100 ? badge("OVER", "danger") : pct >= 80 ? badge("WARNING", "warning") : badge("OK", "success");

        String content = statRow("Category", escHtml(b.getCategory())) +
                statRow("Spent", formatCurrency(spent)) +
                statRow("Limit", formatCurrency(limit)) +
                statRow("Remaining", formatCurrency(remaining)) +
                statRow("Usage", pct + "% " + statusBadge);

        return card("Budget: " + escHtml(b.getCategory()), content + progressItem("Usage", Math.min(pct, 100)));
    }

    private String recentTransactionsResponse(User user, int limit) {
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        List<Income> incomes = incomeRepository.findByUserOrderByIncomeDateDesc(user);

        if (expenses.isEmpty() && incomes.isEmpty()) {
            return card("Recent Transactions", "<p style='color:#888'>No transactions found.</p>");
        }

        List<TransactionView> merged = new ArrayList<>();
        for (Expense e : expenses) merged.add(new TransactionView(e.getDescription(), e.getCategory(), e.getAmount(), e.getExpenseDate(), "EXPENSE"));
        for (Income i : incomes) merged.add(new TransactionView(i.getDescription(), i.getCategory(), i.getAmount(), i.getIncomeDate(), "INCOME"));
        merged.sort((a, b) -> b.date.compareTo(a.date));
        List<TransactionView> top = merged.stream().limit(limit).collect(Collectors.toList());

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Date", "Type", "Description", "Category", "Amount"));
        for (TransactionView t : top) {
            String typeBadge = "EXPENSE".equals(t.type) ? badge("EXPENSE", "danger") : badge("INCOME", "success");
            table.append(tableRow(
                    t.date.toString(),
                    typeBadge,
                    escHtml(t.description),
                    escHtml(t.category),
                    ("EXPENSE".equals(t.type) ? "-" : "+") + formatCurrency(t.amount)
            ));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(card("Recent Transactions (Top " + top.size() + ")", table.toString()));
        if (merged.size() > limit) {
            sb.append("<p style='color:#888;margin-top:4px'>… and " + (merged.size() - limit) + " more. Visit Expenses/Income pages for full history.</p>");
        }
        return sb.toString();
    }

    private String biggestExpensesResponse(User user, int limit, LocalDate[] range) {
        List<Expense> expenses;
        if (range != null && !isDefaultDateRange(range)) {
            expenses = expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]);
        } else {
            expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        }
        if (expenses.isEmpty()) {
            return card("Biggest Expenses", "<p style='color:#888'>No expenses recorded.</p>");
        }

        List<Expense> top = expenses.stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .limit(limit)
                .collect(Collectors.toList());

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("#", "Description", "Category", "Amount", "Date"));
        int rank = 1;
        for (Expense e : top) {
            table.append(tableRow(
                    String.valueOf(rank++),
                    escHtml(e.getDescription()),
                    escHtml(e.getCategory()),
                    formatCurrency(e.getAmount()),
                    e.getExpenseDate().toString()
            ));
        }

        return card("Biggest Expenses (Top " + top.size() + ")", table.toString());
    }

    private String smallestExpensesResponse(User user, int limit) {
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        if (expenses.isEmpty()) {
            return card("Smallest Expenses", "<p style='color:#888'>No expenses recorded.</p>");
        }

        List<Expense> bottom = expenses.stream()
                .sorted((a, b) -> expenseAmount(a).compareTo(expenseAmount(b)))
                .limit(limit)
                .collect(Collectors.toList());

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("#", "Description", "Category", "Amount", "Date"));
        int rank = 1;
        for (Expense e : bottom) {
            table.append(tableRow(
                    String.valueOf(rank++),
                    escHtml(e.getDescription()),
                    escHtml(e.getCategory()),
                    formatCurrency(e.getAmount()),
                    e.getExpenseDate().toString()
            ));
        }

        return card("Smallest Expenses (Top " + bottom.size() + ")", table.toString());
    }

    private String averageExpenseResponse(User user, LocalDate[] range) {
        List<Expense> expenses;
        if (range != null && !isDefaultDateRange(range)) {
            expenses = expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]);
        } else {
            expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        }
        if (expenses.isEmpty()) {
            return card("Average Expense", "<div class=\"fw-chat-stat\"><div class=\"fw-chat-stat-value\">₹0.00</div><div class=\"fw-chat-stat-label\">No expenses to average</div></div>");
        }

        BigDecimal sum = expenses.stream()
                .map(expense -> expenseAmount(expense))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
        BigDecimal avg = sum.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
        BigDecimal min = expenses.stream().map(expense -> expenseAmount(expense)).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = expenses.stream().map(expense -> expenseAmount(expense)).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        String period = describePeriod(range);

        String content = statRow("Average Expense", formatCurrency(avg)) +
                statRow("Smallest", formatCurrency(min)) +
                statRow("Largest", formatCurrency(max)) +
                statRow("Total", formatCurrency(sum)) +
                statRow("Transactions", String.valueOf(expenses.size())) +
                statRow("Period", period);

        return card("Average Expense", content);
    }

    private String incomeVsExpenseResponse(User user, LocalDate[] range) {
        if (range == null) range = currentMonthRange();
        BigDecimal income = incomeRepository.getTotalIncomeByDateRange(user, range[0], range[1]);
        BigDecimal expense = expenseRepository.getTotalExpensesByDateRange(user, range[0], range[1]);
        income = income != null ? income : BigDecimal.ZERO;
        expense = expense != null ? expense : BigDecimal.ZERO;
        BigDecimal balance = income.subtract(expense);
        String period = describePeriod(range);

        String statusBadge;
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            statusBadge = badge("SAVING", "success");
        } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
            statusBadge = badge("OVERSPENDING", "danger");
        } else {
            statusBadge = badge("BREAK-EVEN", "warning");
        }

        String content = statRow("Income", formatCurrency(income)) +
                statRow("Expenses", formatCurrency(expense)) +
                statRow("Net", formatCurrency(balance) + " " + statusBadge) +
                statRow("Period", period);

        return card("Income vs Expenses", content);
    }

    private String goalsProgressResponse(User user) {
        List<FinancialGoal> goals = financialGoalRepository.findByUserOrderByTargetDateAsc(user);
        if (goals.isEmpty()) {
            return card("Financial Goals", "<p style='color:#888'>No financial goals found. Create one to track savings progress.</p>");
        }

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Goal", "Progress", "Target", "Remaining", "Status"));

        for (FinancialGoal g : goals) {
            BigDecimal current = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
            BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO;
            BigDecimal remaining = target.subtract(current).max(BigDecimal.ZERO);
            int pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? current.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP).intValue()
                    : 0;

            String statusBadge;
            if ("COMPLETED".equalsIgnoreCase(g.getGoalStatus())) {
                statusBadge = badge("COMPLETED", "success");
            } else if ("FAILED".equalsIgnoreCase(g.getGoalStatus())) {
                statusBadge = badge("FAILED", "danger");
            } else {
                statusBadge = badge("IN PROGRESS", "info");
            }

            String priorityBadge = badge(g.getPriority(), "warning");

            table.append(tableRow(
                    escHtml(g.getGoalName()) + " " + priorityBadge,
                    pct + "%",
                    formatCurrency(target),
                    formatCurrency(remaining),
                    statusBadge
            ));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(card("Financial Goals", table.toString()));
        sb.append("<div style='margin-top:8px'>");
        for (FinancialGoal g : goals) {
            BigDecimal current = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
            BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO;
            int pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? current.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            sb.append(progressItem(escHtml(g.getGoalName()) + " (" + pct + "%)", Math.min(pct, 100)));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String monthlyReportResponse(User user, LocalDate[] range) {
        if (range == null) range = currentMonthRange();
        BigDecimal totalExpenses = expenseRepository.getTotalExpensesByDateRange(user, range[0], range[1]);
        BigDecimal totalIncome = incomeRepository.getTotalIncomeByDateRange(user, range[0], range[1]);
        totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        BigDecimal balance = totalIncome.subtract(totalExpenses);
        List<Expense> expenses = expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]);
        List<Income> incomes = incomeRepository.findByUserAndIncomeDateBetween(user, range[0], range[1]);
        String period = describePeriod(range);

        if (expenses.isEmpty() && incomes.isEmpty()) {
            return card("Monthly Report — " + period,
                    "<p style='color:#888'>No data for this period. Add income, expenses, or budgets to see a detailed report.</p>");
        }

        // Top expense category
        Map<String, BigDecimal> catTotals = new LinkedHashMap<>();
        for (Expense e : expenses) catTotals.merge(e.getCategory(), expenseAmount(e), (left, right) -> addAmounts(left, right));
        String topCat = catTotals.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + formatCurrency(e.getValue()) + ")").orElse("N/A");

        // Top income source
        Map<String, BigDecimal> incTotals = new LinkedHashMap<>();
        for (Income i : incomes) incTotals.merge(i.getCategory(), incomeAmount(i), (left, right) -> addAmounts(left, right));
        String topInc = incTotals.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + formatCurrency(e.getValue()) + ")").orElse("N/A");

        String savingsBadge;
        if (balance.compareTo(BigDecimal.ZERO) >= 0) {
            savingsBadge = badge("SAVING", "success");
        } else {
            savingsBadge = badge("OVERSPENDING", "danger");
        }

        String content = statRow("Income", formatCurrency(totalIncome) + " (" + incomes.size() + " entries)") +
                statRow("Expenses", formatCurrency(totalExpenses) + " (" + expenses.size() + " entries)") +
                statRow("Net Balance", formatCurrency(balance) + " " + savingsBadge) +
                statRow("Top Expense Category", topCat) +
                statRow("Top Income Source", topInc) +
                statRow("Period", period);

        return card("Monthly Report — " + period, content);
    }

    private String spendingTrendsResponse(User user) {
        YearMonth now = YearMonth.now();
        BigDecimal[] monthlyTotals = new BigDecimal[6];
        String[] monthLabels = new String[6];

        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            BigDecimal total = expenseRepository.getTotalExpensesByDateRange(user, start, end);
            monthlyTotals[5 - i] = total != null ? total : BigDecimal.ZERO;
            monthLabels[5 - i] = ym.getMonth().toString().substring(0, 3) + " " + ym.getYear() % 100;
        }

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Month", "Expenses"));
        for (int i = 0; i < 6; i++) {
            table.append(tableRow(monthLabels[i], formatCurrency(monthlyTotals[i])));
        }

        // Trend analysis
        BigDecimal firstHalf = monthlyTotals[0].add(monthlyTotals[1]).add(monthlyTotals[2]);
        BigDecimal secondHalf = monthlyTotals[3].add(monthlyTotals[4]).add(monthlyTotals[5]);
        String trend;
        if (secondHalf.compareTo(firstHalf) > 0) {
            trend = badge("INCREASING", "danger");
        } else if (secondHalf.compareTo(firstHalf) < 0) {
            trend = badge("DECREASING", "success");
        } else {
            trend = badge("STABLE", "warning");
        }

        String content = statRow("6-Month Trend", trend) +
                statRow("Recent 3 Months Total", formatCurrency(secondHalf)) +
                statRow("Earlier 3 Months Total", formatCurrency(firstHalf));

        return card("Spending Trends (6 Months)", table.toString() + card("Trend Analysis", content));
    }

    private String transactionSearchResponse(User user, String msg) {
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        if (expenses.isEmpty()) {
            return card("Transaction Search", "<p style='color:#888'>No expenses to search.</p>");
        }

        // Extract search term: remove common words
        String searchTerm = msg.replaceAll("(search|find|transactions?|expenses?|with|show|me|for|containing)", "").trim();
        if (searchTerm.length() < 2) {
            searchTerm = "";
        }

        String finalSearchTerm = searchTerm;
        List<Expense> results = expenses.stream()
                .filter(e -> finalSearchTerm.isEmpty() ||
                        e.getDescription().toLowerCase().contains(finalSearchTerm) ||
                        e.getCategory().toLowerCase().contains(finalSearchTerm) ||
                        (e.getNotes() != null && e.getNotes().toLowerCase().contains(finalSearchTerm)))
                .limit(10)
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            return card("Transaction Search", "<p style='color:#888'>No transactions found matching \"<strong>" + escHtml(searchTerm) + "</strong>\".</p>");
        }

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Date", "Description", "Category", "Amount"));
        for (Expense e : results) {
            table.append(tableRow(
                    e.getExpenseDate().toString(),
                    escHtml(e.getDescription()),
                    escHtml(e.getCategory()),
                    formatCurrency(e.getAmount())
            ));
        }

        return card("Transaction Search Results (" + results.size() + " found)", table.toString());
    }

    private String countRecordsResponse(User user, String msg) {
        long expenseCount = expenseRepository.findByUserOrderByExpenseDateDesc(user).size();
        long incomeCount = incomeRepository.findByUserOrderByIncomeDateDesc(user).size();
        long budgetCount = budgetRepository.findByUserAndIsActiveTrue(user).size();
        long goalCount = financialGoalRepository.findByUserOrderByTargetDateAsc(user).size();
        long accountCount = accountRepository.findByUserAndIsActiveTrueOrderByAccountNameAsc(user).size();

        String content;
        if (msg.contains("expense")) {
            content = statRow("Expenses", String.valueOf(expenseCount));
        } else if (msg.contains("income")) {
            content = statRow("Income Records", String.valueOf(incomeCount));
        } else if (msg.contains("budget")) {
            content = statRow("Active Budgets", String.valueOf(budgetCount));
        } else if (msg.contains("goal")) {
            content = statRow("Goals", String.valueOf(goalCount));
        } else if (msg.contains("account")) {
            content = statRow("Accounts", String.valueOf(accountCount));
        } else {
            content = statRow("Expenses", String.valueOf(expenseCount)) +
                    statRow("Income Records", String.valueOf(incomeCount)) +
                    statRow("Active Budgets", String.valueOf(budgetCount)) +
                    statRow("Goals", String.valueOf(goalCount)) +
                    statRow("Accounts", String.valueOf(accountCount));
        }

        return card("Record Counts", content);
    }

    private String accountsResponse(User user) {
        List<Account> accounts = accountRepository.findByUserAndIsActiveTrueOrderByAccountNameAsc(user);
        if (accounts.isEmpty()) {
            List<Account> all = accountRepository.findByUserOrderByCreatedAtDesc(user);
            if (all.isEmpty()) {
                return card("Accounts", "<p style='color:#888'>No accounts found. Create one to track balances.</p>");
            }
            return card("Accounts", "<p style='color:#888'>No active accounts. You have " + all.size() + " inactive account(s).</p>");
        }

        BigDecimal total = accounts.stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Account", "Type", "Institution", "Balance"));
        for (Account a : accounts) {
            table.append(tableRow(
                    escHtml(a.getAccountName()),
                    escHtml(a.getAccountType()),
                    a.getInstitution() != null ? escHtml(a.getInstitution()) : "—",
                    formatCurrency(a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
            ));
        }

        return card("Your Accounts", table.toString() + card("Total", statRow("Total Balance", formatCurrency(total))));
    }

    private String userProfileResponse(User user) {
        String content = statRow("Full Name", escHtml(user.getFullName())) +
                statRow("Username", escHtml(user.getUsername())) +
                statRow("Email", escHtml(user.getEmail())) +
                statRow("Member Since", user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate().toString() : "Unknown") +
                statRow("User ID", String.valueOf(user.getId())) +
                "<p style='color:#888;margin-top:8px;font-size:12px'>All data is scoped to your account and isolated from other users.</p>";

        return card("Your Profile", content);
    }

    private String recurringSpendingResponse(User user) {
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        if (expenses.isEmpty()) {
            return card("Recurring Spending", "<p style='color:#888'>No expenses to analyze for recurring patterns.</p>");
        }

        // Group by category and calculate monthly averages
        Map<String, List<Expense>> byCategory = expenses.stream().collect(Collectors.groupingBy(
                expense -> expense.getCategory() != null ? expense.getCategory() : "Other"));
        Map<String, BigDecimal> monthlyAvg = new LinkedHashMap<>();

        for (Map.Entry<String, List<Expense>> e : byCategory.entrySet()) {
            BigDecimal total = e.getValue().stream()
                    .map(expense -> expenseAmount(expense))
                    .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
            // Estimate months span
            LocalDate earliest = e.getValue().stream()
                    .map(expense -> expense.getExpenseDate())
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
            LocalDate latest = e.getValue().stream()
                    .map(expense -> expense.getExpenseDate())
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
            long months = Math.max(1, java.time.temporal.ChronoUnit.MONTHS.between(earliest.withDayOfMonth(1), latest.withDayOfMonth(1)) + 1);
            BigDecimal avg = total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            monthlyAvg.put(e.getKey(), avg);
        }

        StringBuilder table = new StringBuilder();
        table.append(tableHeader("Category", "Avg Monthly", "Transactions", "Total"));
        monthlyAvg.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> {
                    List<Expense> catExpenses = byCategory.get(e.getKey());
                    BigDecimal total = catExpenses.stream()
                            .map(expense -> expenseAmount(expense))
                            .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
                    table.append(tableRow(escHtml(e.getKey()), formatCurrency(e.getValue()), String.valueOf(catExpenses.size()), formatCurrency(total)));
                });

        return card("Recurring Spending Analysis", table.toString() + "<p style='color:#888;margin-top:8px;font-size:12px'>Based on historical spending patterns across all recorded expenses.</p>");
    }

    private String insightsResponse(User user) {
        BigDecimal totalExpenses = expenseRepository.getTotalExpensesByDateRange(user, LocalDate.of(2000, 1, 1), LocalDate.now());
        BigDecimal totalIncome = incomeRepository.getTotalIncomeByDateRange(user, LocalDate.of(2000, 1, 1), LocalDate.now());
        totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;

        List<Budget> budgets = budgetRepository.findByUserAndIsActiveTrue(user);
        List<FinancialGoal> goals = financialGoalRepository.findByUserOrderByTargetDateAsc(user);
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);

        StringBuilder tips = new StringBuilder();

        // Savings rate
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savingsRate = totalIncome.subtract(totalExpenses).multiply(BigDecimal.valueOf(100)).divide(totalIncome, 1, RoundingMode.HALF_UP);
            if (savingsRate.compareTo(BigDecimal.ZERO) < 0) {
                tips.append("<p>" + badge("ALERT", "danger") + " Your expenses exceed your income. Consider reducing spending or increasing income.</p>");
            } else if (savingsRate.compareTo(BigDecimal.valueOf(20)) < 0) {
                tips.append("<p>" + badge("TIP", "warning") + " Your savings rate is " + savingsRate + "%. Aim for 20% or higher.</p>");
            } else {
                tips.append("<p>" + badge("GREAT", "success") + " Your savings rate is " + savingsRate + "%. You're doing well!</p>");
            }
        }

        // Over-budget warnings
        for (Budget b : budgets) {
            BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
            BigDecimal limit = b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO;
            if (limit.compareTo(BigDecimal.ZERO) > 0 && spent.compareTo(limit) > 0) {
                tips.append("<p>" + badge("OVER BUDGET", "danger") + " " + escHtml(b.getCategory()) + ": spent " + formatCurrency(spent) + " of " + formatCurrency(limit) + "</p>");
            }
        }

        // Goal progress
        for (FinancialGoal g : goals) {
            BigDecimal current = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
            BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO;
            if (target.compareTo(BigDecimal.ZERO) > 0) {
                int pct = current.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP).intValue();
                if (pct < 25) {
                    tips.append("<p>" + badge("GOAL", "info") + " " + escHtml(g.getGoalName()) + " is only " + pct + "% complete. Consider increasing contributions.</p>");
                }
            }
        }

        // Top spending category
        if (!expenses.isEmpty()) {
            Map<String, BigDecimal> cats = new HashMap<>();
            for (Expense e : expenses) cats.merge(e.getCategory(), expenseAmount(e), (left, right) -> addAmounts(left, right));
            cats.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(top ->
                    tips.append("<p>" + badge("TOP SPENDING", "warning") + " " + escHtml(top.getKey()) + ": " + formatCurrency(top.getValue()) + " total</p>")
            );
        }

        if (tips.length() == 0) {
            tips.append("<p style='color:#888'>Not enough data to generate insights. Keep tracking your finances!</p>");
        }

        return card("Financial Insights", tips.toString());
    }

    // ──────────────────────────── Static Helpers ────────────────────────────

    private String greetingResponse(User user) {
        String name = user.getFullName() != null ? user.getFullName() : user.getUsername();
        return card("Hello, " + escHtml(name) + "!",
                "I'm your <strong>Offline Finance Assistant</strong>. I read only your local database — no internet, no cloud.\n\n" +
                "Ask me about:\n" +
                "• Expenses, income, budgets\n" +
                "• Transactions & accounts\n" +
                "• Financial goals & insights\n\n" +
                "Try: <em>\"How much did I spend this month?\"</em>");
    }

    private String helpResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append(card("How I Can Help", ""));
        sb.append("<div style='padding:0 12px 12px'>");
        sb.append("<strong>Expenses</strong><br>");
        sb.append("• \"How much did I spend this month?\"<br>");
        sb.append("• \"Spending by category\"<br>");
        sb.append("• \"Biggest expenses\" / \"Smallest expenses\"<br>");
        sb.append("• \"Average expense\"<br><br>");
        sb.append("<strong>Income</strong><br>");
        sb.append("• \"What is my monthly income?\"<br>");
        sb.append("• \"Income by category\"<br>");
        sb.append("• \"Total income\"<br><br>");
        sb.append("<strong>Budgets</strong><br>");
        sb.append("• \"Budget status\" / \"Budget remaining\"<br>");
        sb.append("• \"Food budget\" (specific category)<br><br>");
        sb.append("<strong>Transactions</strong><br>");
        sb.append("• \"Recent transactions\"<br>");
        sb.append("• \"Search transactions with food\"<br><br>");
        sb.append("<strong>Reports & Insights</strong><br>");
        sb.append("• \"Monthly report\"<br>");
        sb.append("• \"Spending trends\"<br>");
        sb.append("• \"Income vs expenses\"<br>");
        sb.append("• \"Financial insights\"<br><br>");
        sb.append("<strong>Other</strong><br>");
        sb.append("• \"My profile\" / \"My accounts\"<br>");
        sb.append("• \"Goals progress\"<br>");
        sb.append("• \"How many expenses?\"<br>");
        sb.append("• \"Clear context\" (reset conversation)");
        sb.append("</div>");
        return sb.toString();
    }

    private String whoAreYouResponse() {
        return card("About Me",
                "I'm your <strong>Offline Finance Assistant</strong>. " +
                "I work 100% offline using only your local database. " +
                "No internet, no external APIs, no cloud services. " +
                "Your data stays private and secure on your own system.");
    }

    private String emptyMessage() {
        return card("Empty Message",
                "I didn't catch that. Try asking:<br>" +
                "• \"How much did I spend this month?\"<br>" +
                "• \"What is my monthly income?\"<br>" +
                "• \"Show recent transactions\"<br>" +
                "• \"Budget status\"");
    }

    private String fallbackResponse() {
        return card("I'm Not Sure",
                "I can help you with your income, expenses, balance, budgets, goals, and transactions. Try asking something like 'How much did I spend this month?'<br><br>" +
                "Examples:<br>" +
                "• \"How much did I spend this month?\"<br>" +
                "• \"What is my monthly income?\"<br>" +
                "• \"Show recent transactions\"<br>" +
                "• \"Budget status\" / \"Budget remaining\"<br>" +
                "• \"Spending by category\"<br>" +
                "• \"Goals progress\"<br>" +
                "• \"Monthly report\"");
    }

    // ──────────────────────────── HTML Formatting Helpers ────────────────────────────

    private Map<String, Object> buildChartData(String title, List<String> labels, List<BigDecimal> values) {
        Map<String, Object> chart = new HashMap<>();
        chart.put("title", title);
        chart.put("labels", labels);
        chart.put("values", values.stream().map(v -> v != null ? v.doubleValue() : 0.0).collect(Collectors.toList()));
        return chart;
    }

    private Map<String, Object> generateChartForIntent(User user, String intent, LocalDate[] range) {
        try {
            List<String> labels = new ArrayList<>();
            List<BigDecimal> values = new ArrayList<>();
            if (intent.equals("EXPENSES_BY_CATEGORY")) {
                List<Expense> expenses = (range != null && !isDefaultDateRange(range)) ? expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]) : expenseRepository.findByUserOrderByExpenseDateDesc(user);
                Map<String, BigDecimal> totals = new LinkedHashMap<>();
                for (Expense e : expenses) totals.merge(e.getCategory(), expenseAmount(e), (left, right) -> addAmounts(left, right));
                for (Map.Entry<String, BigDecimal> e : totals.entrySet().stream().sorted((a,b)->b.getValue().compareTo(a.getValue())).limit(6).collect(Collectors.toList())) {
                    labels.add(e.getKey());
                    values.add(e.getValue());
                }
                return buildChartData("Spending by Category", labels, values);
            } else if (intent.equals("INCOME_BY_CATEGORY")) {
                List<Income> incomes = incomeRepository.findByUserOrderByIncomeDateDesc(user);
                Map<String, BigDecimal> totals = new LinkedHashMap<>();
                for (Income i : incomes) totals.merge(i.getCategory(), incomeAmount(i), (left, right) -> addAmounts(left, right));
                for (Map.Entry<String, BigDecimal> e : totals.entrySet().stream().sorted((a,b)->b.getValue().compareTo(a.getValue())).limit(6).collect(Collectors.toList())) {
                    labels.add(e.getKey());
                    values.add(e.getValue());
                }
                return buildChartData("Income by Category", labels, values);
            } else if (intent.equals("SPENDING_TRENDS")) {
                YearMonth now = YearMonth.now();
                for (int i = 5; i >= 0; i--) {
                    YearMonth ym = now.minusMonths(i);
                    labels.add(ym.getMonth().toString().substring(0,3) + " " + (ym.getYear() % 100));
                    BigDecimal total = expenseRepository.getTotalExpensesByDateRange(user, ym.atDay(1), ym.atEndOfMonth());
                    values.add(total != null ? total : BigDecimal.ZERO);
                }
                return buildChartData("Spending Trend (6 Months)", labels, values);
            } else if (intent.equals("MONTHLY_REPORT")) {
                if (range == null) range = currentMonthRange();
                List<Expense> expenses = expenseRepository.findByUserAndExpenseDateBetween(user, range[0], range[1]);
                List<Income> incomes = incomeRepository.findByUserAndIncomeDateBetween(user, range[0], range[1]);
                BigDecimal totalExpenses = expenses.stream()
                        .map(expense -> expenseAmount(expense))
                        .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
                BigDecimal totalIncome = incomes.stream()
                        .map(income -> incomeAmount(income))
                        .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
                labels.add("Income");
                values.add(totalIncome);
                labels.add("Expenses");
                values.add(totalExpenses);
                return buildChartData("Monthly Report — " + describePeriod(range), labels, values);
            } else if (intent.equals("INCOME_VS_EXPENSE")) {
                if (range == null) range = currentMonthRange();
                BigDecimal income = incomeRepository.getTotalIncomeByDateRange(user, range[0], range[1]);
                BigDecimal expense = expenseRepository.getTotalExpensesByDateRange(user, range[0], range[1]);
                income = income != null ? income : BigDecimal.ZERO;
                expense = expense != null ? expense : BigDecimal.ZERO;
                labels.add("Income");
                values.add(income);
                labels.add("Expenses");
                values.add(expense);
                return buildChartData("Income vs Expenses — " + describePeriod(range), labels, values);
            }
        } catch (Exception e) {
            // Ignore chart errors
        }
        return null;
    }

    private String card(String title, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"fw-chat-card\">");
        sb.append("<div class=\"fw-chat-card-header\">").append(escHtml(title)).append("</div>");
        if (body != null && !body.isEmpty()) {
            sb.append("<div class=\"fw-chat-card-body\">").append(body).append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String tableHeader(String... cols) {
        StringBuilder sb = new StringBuilder("<div class=\"fw-chat-table\">");
        sb.append("<div class=\"fw-chat-row fw-chat-header\">");
        for (String col : cols) {
            sb.append("<span>").append(escHtml(col)).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String tableRow(String... vals) {
        StringBuilder sb = new StringBuilder("<div class=\"fw-chat-row\">");
        for (String val : vals) {
            sb.append("<span>").append(val).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String progressItem(String label, int pct) {
        return "<div style='margin-bottom:6px'>" +
                "<div style='display:flex;justify-content:space-between;margin-bottom:2px'>" +
                "<span style='font-size:12px'>" + label + "</span>" +
                "<span style='font-size:12px;color:#888'>" + pct + "%</span>" +
                "</div>" +
                "<div class=\"fw-chat-progress\">" +
                "<div class=\"fw-chat-progress-bar\" style=\"width:" + Math.min(pct, 100) + "%\"></div>" +
                "</div></div>";
    }

    private String statRow(String label, String value) {
        return "<div class=\"fw-chat-stat\" style=\"display:flex;justify-content:space-between;padding:3px 0;border-bottom:1px solid #f0f0f0\">" +
                "<span style='color:#666;font-size:13px'>" + label + "</span>" +
                "<span style='font-weight:600;font-size:13px'>" + value + "</span></div>";
    }

    private String badge(String text, String type) {
        return "<span class=\"fw-chat-badge fw-chat-badge-" + type + "\">" + escHtml(text) + "</span>";
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

    private String formatCurrency(BigDecimal val) {
        if (val == null) return "₹0.00";
        return "₹" + val.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String describePeriod(LocalDate[] range) {
        if (range == null) return "All Time";
        LocalDate start = range[0];
        LocalDate end = range[1];
        YearMonth now = YearMonth.now();
        YearMonth startYM = YearMonth.from(start);
        YearMonth endYM = YearMonth.from(end);

        if (start.equals(now.atDay(1)) && end.equals(now.atEndOfMonth())) return "This Month";
        YearMonth lastMonth = now.minusMonths(1);
        if (start.equals(lastMonth.atDay(1)) && end.equals(lastMonth.atEndOfMonth())) return "Last Month";
        if (start.getYear() == now.getYear() && start.getMonthValue() == 1 && end.getMonthValue() == 12) return "This Year";
        if (startYM.equals(endYM)) return startYM.getMonth().toString() + " " + start.getYear();
        return start + " to " + end;
    }

    private boolean matchesAny(String msg, String... keywords) {
        for (String kw : keywords) if (msg.contains(kw)) return true;
        return false;
    }

    private List<String> defaultSuggestions() {
        return List.of("How much did I spend this month?", "Show my recent transactions", "Budget status");
    }

    private ChatResult buildResult(String intent, double confidence, String message, List<String> suggestions) {
        lastConfidence = confidence;
        lastIntent = intent;
        lastSuggestions = suggestions;
        return new ChatResult(message, confidence, intent, suggestions);
    }

    // ──────────────────────────── Helper DTO ────────────────────────────

    private static class TransactionView {
        String description;
        String category;
        BigDecimal amount;
        LocalDate date;
        String type;

        TransactionView(String d, String c, BigDecimal a, LocalDate dt, String t) {
            description = d;
            category = c;
            amount = a;
            date = dt;
            type = t;
        }
    }
}
