package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
public class LocalForecastEngine implements ForecastEngine {

    private final LocalAnalyticsEngine analyticsEngine = new LocalAnalyticsEngine();

    @Override
    public ForecastResult forecastForMonth(List<ImportedTransaction> transactions, YearMonth month) {
        BigDecimal predictedSpending = forecastSpending(transactions);
        if (month != null && transactions != null) {
            BigDecimal monthTotal = transactions.stream()
                    .filter(tx -> tx != null && tx.getAmount() != null && tx.getTransactionDate() != null)
                    .filter(tx -> "EXPENSE".equalsIgnoreCase(tx.getTransactionType()))
                    .filter(tx -> YearMonth.from(tx.getTransactionDate()).equals(month))
                    .map(tx -> tx.getAmount().abs())
                    .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
            if (monthTotal.compareTo(BigDecimal.ZERO) > 0) {
                predictedSpending = monthTotal;
            }
        }
        BigDecimal currentSpending = analyticsEngine.calculateCurrentSpending(transactions);
        BigDecimal previousMonthComparison = analyticsEngine.calculatePreviousMonthComparison(transactions);
        String trend = getTrend(transactions);
        double confidence = analyticsEngine.calculateForecastConfidence(transactions);
        return new ForecastResult(predictedSpending, currentSpending, previousMonthComparison, trend, confidence);
    }

    @Override
    public BigDecimal forecastSpending(List<ImportedTransaction> transactions) {
        return analyticsEngine.calculateMonthlySpendingPrediction(transactions);
    }

    @Override
    public BigDecimal forecastIncome(List<ImportedTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return transactions.stream()
                .filter(tx -> tx != null && tx.getAmount() != null && "INCOME".equalsIgnoreCase(tx.getTransactionType()))
                .map(transaction -> transaction.getAmount())
                .reduce(BigDecimal.ZERO, (left, right) -> addAmounts(left, right));
    }

    private BigDecimal addAmounts(BigDecimal left, BigDecimal right) {
        return Objects.requireNonNullElse(left, BigDecimal.ZERO)
                .add(Objects.requireNonNullElse(right, BigDecimal.ZERO));
    }

    @Override
    public String getTrend(List<ImportedTransaction> transactions) {
        BigDecimal comparison = analyticsEngine.calculatePreviousMonthComparison(transactions);
        if (comparison.compareTo(BigDecimal.ZERO) > 0) {
            return "Increasing";
        }
        if (comparison.compareTo(BigDecimal.ZERO) < 0) {
            return "Decreasing";
        }
        return "Stable";
    }
}
