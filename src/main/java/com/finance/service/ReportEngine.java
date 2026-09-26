package com.finance.service;

import com.finance.model.entity.ImportedTransaction;

import java.util.List;
import java.util.Map;

public interface ReportEngine {
    Map<String, Object> generateMonthlyExpenseReport(List<ImportedTransaction> transactions);

    Map<String, Object> generateIncomeReport(List<ImportedTransaction> transactions);

    Map<String, Object> generateCategoryReport(List<ImportedTransaction> transactions);

    Map<String, Object> generateBudgetReport(List<ImportedTransaction> transactions);

    Map<String, Object> generateImportReport(List<ImportedTransaction> transactions);
}
