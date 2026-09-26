package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LocalReportEngine implements ReportEngine {

    @Override
    public Map<String, Object> generateMonthlyExpenseReport(List<ImportedTransaction> transactions) {
        Map<String, Object> report = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ImportedTransaction tx : transactions) {
            if (tx != null && tx.getAmount() != null && "EXPENSE".equalsIgnoreCase(tx.getTransactionType())) {
                total = total.add(tx.getAmount());
                count++;
            }
        }
        report.put("title", "Monthly Expense Report");
        report.put("total", total);
        report.put("count", count);
        return report;
    }

    @Override
    public Map<String, Object> generateIncomeReport(List<ImportedTransaction> transactions) {
        Map<String, Object> report = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ImportedTransaction tx : transactions) {
            if (tx != null && tx.getAmount() != null && "INCOME".equalsIgnoreCase(tx.getTransactionType())) {
                total = total.add(tx.getAmount());
                count++;
            }
        }
        report.put("title", "Income Report");
        report.put("total", total);
        report.put("count", count);
        return report;
    }

    @Override
    public Map<String, Object> generateCategoryReport(List<ImportedTransaction> transactions) {
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ImportedTransaction tx : transactions) {
            if (tx != null && tx.getAmount() != null && tx.getCategory() != null) {
                byCategory.merge(tx.getCategory(), tx.getAmount(), (left, right) -> addAmounts(left, right));
            }
        }
        report.put("title", "Category Report");
        report.put("categories", byCategory);
        return report;
    }

    private BigDecimal addAmounts(BigDecimal left, BigDecimal right) {
        return Objects.requireNonNullElse(left, BigDecimal.ZERO)
                .add(Objects.requireNonNullElse(right, BigDecimal.ZERO));
    }

    @Override
    public Map<String, Object> generateBudgetReport(List<ImportedTransaction> transactions) {
        Map<String, Object> report = generateMonthlyExpenseReport(transactions);
        report.put("title", "Budget Report");
        report.put("month", YearMonth.now().toString());
        return report;
    }

    @Override
    public Map<String, Object> generateImportReport(List<ImportedTransaction> transactions) {
        Map<String, Object> report = new LinkedHashMap<>();
        int parsed = 0;
        int validated = 0;
        for (ImportedTransaction tx : transactions) {
            if (tx == null) {
                continue;
            }
            if ("PARSED".equalsIgnoreCase(tx.getImportStatus())) {
                parsed++;
            }
            if ("VALIDATED".equalsIgnoreCase(tx.getImportStatus()) || "IMPORTED".equalsIgnoreCase(tx.getImportStatus())) {
                validated++;
            }
        }
        report.put("title", "Import / OCR Report");
        report.put("parsed", parsed);
        report.put("validated", validated);
        report.put("total", transactions == null ? 0 : transactions.size());
        return report;
    }
}
