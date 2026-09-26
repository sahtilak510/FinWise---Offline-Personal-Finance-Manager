package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TransactionExtractor {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})\\b");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:[$€£]|inr|rs\\.?|usd\\.?)?\\s*(-?\\d+(?:[.,]\\d{1,2})?)");

    public List<ImportedTransaction> extractTransactions(String text, String originalFilename, User user, String fileType) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<ImportedTransaction> records = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }

            Matcher amountMatcher = AMOUNT_PATTERN.matcher(line);
            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            if (!amountMatcher.find() || !dateMatcher.find()) {
                continue;
            }

            ImportedTransaction record = new ImportedTransaction();
            record.setUser(user);
            record.setOriginalFilename(originalFilename == null ? "unknown" : originalFilename);
            record.setFileType(fileType == null ? "UNKNOWN" : fileType.toUpperCase(Locale.ROOT));
            record.setDescription(sanitizeDescription(line));
            record.setTransactionDate(parseDate(dateMatcher.group(1)));
            record.setAmount(parseAmount(amountMatcher.group(1)));
            record.setTransactionType(normalizeTransactionType(line));
            record.setCategory("Uncategorized");
            record.setImportStatus("PARSED");
            records.add(record);
        }

        return records;
    }

    public String sanitizeDescription(String text) {
        if (text == null) {
            return "Transaction";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    public String normalizeTransactionType(String text) {
        if (text == null) {
            return "EXPENSE";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("salary") || normalized.contains("deposit") || normalized.contains("refund") || normalized.contains("income") || normalized.contains("credit")) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    public LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return LocalDate.now();
        }
        String cleaned = dateText.trim().replaceAll("\\s+", " ");
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd MMM yyyy"),
                DateTimeFormatter.ofPattern("MMM dd, yyyy")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }

        try {
            return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (DateTimeParseException ignored) {
            // Fall back to today.
        }
        return LocalDate.now();
    }

    public BigDecimal parseAmount(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = amountText.replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace("Rs", "")
                .replace("rs", "")
                .replace(",", "")
                .trim();

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
