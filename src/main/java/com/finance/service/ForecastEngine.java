package com.finance.service;

import com.finance.model.entity.ImportedTransaction;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public interface ForecastEngine {
    ForecastResult forecastForMonth(List<ImportedTransaction> transactions, YearMonth month);

    BigDecimal forecastSpending(List<ImportedTransaction> transactions);

    BigDecimal forecastIncome(List<ImportedTransaction> transactions);

    String getTrend(List<ImportedTransaction> transactions);

    class ForecastResult {
        private final BigDecimal predictedSpending;
        private final BigDecimal currentSpending;
        private final BigDecimal previousMonthComparison;
        private final String trend;
        private final double confidence;

        public ForecastResult(BigDecimal predictedSpending, BigDecimal currentSpending,
                             BigDecimal previousMonthComparison, String trend, double confidence) {
            this.predictedSpending = predictedSpending;
            this.currentSpending = currentSpending;
            this.previousMonthComparison = previousMonthComparison;
            this.trend = trend;
            this.confidence = confidence;
        }

        public BigDecimal getPredictedSpending() {
            return predictedSpending;
        }

        public BigDecimal getCurrentSpending() {
            return currentSpending;
        }

        public BigDecimal getPreviousMonthComparison() {
            return previousMonthComparison;
        }

        public String getTrend() {
            return trend;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}
