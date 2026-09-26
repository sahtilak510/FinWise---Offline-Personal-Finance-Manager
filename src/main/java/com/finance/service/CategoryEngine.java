package com.finance.service;

import com.finance.model.entity.ImportedTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface CategoryEngine {
    String categorize(String description, String merchant, BigDecimal amount, String transactionType);

    default String categorize(ImportedTransaction transaction) {
        if (transaction == null) {
            return "Other";
        }
        return categorize(
                transaction.getDescription(),
                extractMerchant(transaction.getDescription()),
                transaction.getAmount(),
                transaction.getTransactionType()
        );
    }

    List<String> getSupportedCategories();

    default String extractMerchant(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String[] parts = description.replaceAll("[|\\-]", " ").split("\\s+");
        if (parts.length == 0) {
            return "";
        }
        return parts[0];
    }
}
