package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class LocalAnalyticsEngine implements AnalyticsEngine {

    private static final LocalCategoryEngine CATEGORY_ENGINE = new LocalCategoryEngine();

    @Override
    public BigDecimal calculateMonthlySpendingPrediction(List<ImportedTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<YearMonth, BigDecimal> totalsByMonth = new TreeMap<>();
        for (ImportedTransaction tx : transactions) {
            if (tx == null || tx.getAmount() == null || tx.getTransactionDate() == null) {
                continue;
            }
            if (!"EXPENSE".equalsIgnoreCase(tx.getTransactionType())) {
                continue;
            }
            YearMonth month = transactionMonth(tx);
            if (month == null) continue;
            totalsByMonth.merge(month, normalizeExpenseAmount(tx.getAmount()), (left, right) -> addAmounts(left, right));
        }

        if (totalsByMonth.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = totalsByMonth.values().stream().reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
        return total.divide(BigDecimal.valueOf(totalsByMonth.size()), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateCurrentSpending(List<ImportedTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        YearMonth currentMonth = YearMonth.now();
        return transactions.stream()
                .filter(tx -> transactionMonth(tx) != null)
                .filter(tx -> "EXPENSE".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> currentMonth.equals(transactionMonth(tx)))
                .map(tx -> normalizeExpenseAmount(tx.getAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
    }

    @Override
    public BigDecimal calculatePreviousMonthComparison(List<ImportedTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        BigDecimal currentTotal = transactions.stream()
                .filter(tx -> transactionMonth(tx) != null)
                .filter(tx -> "EXPENSE".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> currentMonth.equals(transactionMonth(tx)))
                .map(tx -> normalizeExpenseAmount(tx.getAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        BigDecimal previousTotal = transactions.stream()
                .filter(tx -> transactionMonth(tx) != null)
                .filter(tx -> "EXPENSE".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> previousMonth.equals(transactionMonth(tx)))
                .map(tx -> normalizeExpenseAmount(tx.getAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));

        return currentTotal.subtract(previousTotal);
    }

    @Override
    public Map<String, BigDecimal> calculateCategoryTrends(List<ImportedTransaction> transactions) {
        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        if (transactions == null) {
            return categoryTotals;
        }

        for (ImportedTransaction tx : transactions) {
            if (tx == null || tx.getAmount() == null || tx.getTransactionDate() == null) {
                continue;
            }
            if (!"EXPENSE".equalsIgnoreCase(tx.getTransactionType())) {
                continue;
            }
            String category = tx.getCategory();
            if (category == null || category.isBlank()) {
                category = CATEGORY_ENGINE.categorize(tx);
            }
            if (category == null || category.isBlank()) {
                continue;
            }
            categoryTotals.merge(category, normalizeExpenseAmount(tx.getAmount()), (left, right) -> addAmounts(left, right));
        }
        return categoryTotals;
    }

    @Override
    public double calculateForecastConfidence(List<ImportedTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return 0.0d;
        }
        long validCount = transactions.stream()
                .filter(tx -> tx != null && tx.getTransactionDate() != null && tx.getAmount() != null)
                .count();
        double sampleRatio = validCount / (double) Math.max(1, transactions.size());
        return Math.min(0.95d, 0.55d + sampleRatio * 0.4d);
    }

    private YearMonth transactionMonth(ImportedTransaction transaction) {
        if (transaction == null) return null;
        return transaction.getTransactionDate() == null ? null : YearMonth.from(transaction.getTransactionDate());
    }

    private BigDecimal addAmounts(BigDecimal left, BigDecimal right) {
        return normalizeExpenseAmount(left).add(normalizeExpenseAmount(right));
    }

    private BigDecimal normalizeExpenseAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.abs();
    }
}
