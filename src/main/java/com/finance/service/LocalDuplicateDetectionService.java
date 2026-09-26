package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class LocalDuplicateDetectionService implements DuplicateDetectionService {

    @Override
    public List<ImportedTransaction> detectDuplicates(List<ImportedTransaction> transactions) {
        List<ImportedTransaction> duplicates = new ArrayList<>();
        if (transactions == null || transactions.isEmpty()) {
            return duplicates;
        }

        for (int i = 0; i < transactions.size(); i++) {
            for (int j = i + 1; j < transactions.size(); j++) {
                ImportedTransaction left = transactions.get(i);
                ImportedTransaction right = transactions.get(j);
                if (isDuplicate(left, right)) {
                    duplicates.add(right);
                }
            }
        }
        return duplicates;
    }

    @Override
    public boolean isDuplicate(ImportedTransaction first, ImportedTransaction second) {
        if (first == null || second == null) {
            return false;
        }

        if (!sameUser(first, second)) {
            return false;
        }

        if (!Objects.equals(normalizeType(first.getTransactionType()), normalizeType(second.getTransactionType()))) {
            return false;
        }

        boolean sameDate = sameDate(first.getTransactionDate(), second.getTransactionDate());
        boolean sameAmount = sameAmount(first.getAmount(), second.getAmount());
        boolean sameDescription = sameText(first.getDescription(), second.getDescription());
        String merchant1 = extractMerchant(first.getDescription());
        String merchant2 = extractMerchant(second.getDescription());
        boolean sameMerchant = sameText(merchant1, merchant2);

        int matches = 0;
        if (sameDate) matches++;
        if (sameAmount) matches++;
        if (sameDescription) matches++;
        if (sameMerchant) matches++;

        return matches >= 2 && (sameDate || sameAmount || sameDescription || sameMerchant);
    }

    private boolean sameUser(ImportedTransaction first, ImportedTransaction second) {
        if (first.getUser() == null && second.getUser() == null) {
            return true;
        }
        if (first.getUser() == null || second.getUser() == null) {
            return false;
        }
        return Objects.equals(first.getUser().getId(), second.getUser().getId());
    }

    private boolean sameDate(LocalDate left, LocalDate right) {
        return left != null && right != null && left.equals(right);
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        return left.subtract(right).abs().compareTo(new BigDecimal("0.01")) <= 0;
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeText(left).equals(normalizeText(right));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "EXPENSE";
        }
        return type.trim().toUpperCase();
    }

    private String extractMerchant(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String cleaned = description.replaceAll("[^a-zA-Z0-9\\s]", " ");
        String[] parts = cleaned.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }
}
