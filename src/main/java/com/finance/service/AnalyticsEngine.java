package com.finance.service;

import com.finance.model.entity.ImportedTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AnalyticsEngine {
    BigDecimal calculateMonthlySpendingPrediction(List<ImportedTransaction> transactions);
    BigDecimal calculateCurrentSpending(List<ImportedTransaction> transactions);
    BigDecimal calculatePreviousMonthComparison(List<ImportedTransaction> transactions);
    Map<String, BigDecimal> calculateCategoryTrends(List<ImportedTransaction> transactions);
    double calculateForecastConfidence(List<ImportedTransaction> transactions);
}
