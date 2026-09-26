package com.finance.service;

import com.finance.model.dto.ExtractedTransactionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scan &amp; Fill parsing step (separation of concerns).
 *
 * <p>Takes raw text produced by the PDF/OCR layer and heuristically derives a
 * single {@link ExtractedTransactionDTO}. It does NOT claim to understand every
 * bank statement: when confidence is low the field is left empty and a warning
 * is recorded instead of inventing data.</p>
 */
@Component
@RequiredArgsConstructor
public class ScanTransactionParser {

    private final CategoryEngine categoryEngine;

    // ₹1,299 | Rs. 1299 | INR 1299 | 1,299.00 | $45.50
    // Bare numbers are deliberately NOT matched here: date fragments such as
    // the "2026" in "08-09-2026" must never be mistaken for money.
    private static final Pattern CURRENCY_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr|\\$|€|£|usd)\\s*([\\d,]+(?:\\.\\d{1,2})?)"
                    + "|([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:₹|rs\\.?|inr)");

    // Decimal numbers without a currency symbol, e.g. "1299.00".
    private static final Pattern DECIMAL_AMOUNT_PATTERN =
            Pattern.compile("(?<!\\d)(\\d[\\d,]*\\.\\d{1,2})(?!\\d)");

    // Plain integers count as money ONLY on total lines
    // ("Grand Total: 1299"), never elsewhere (invoice/order numbers, years).
    private static final Pattern BARE_AMOUNT_PATTERN =
            Pattern.compile("(?<!\\d)(\\d[\\d,]*)(?!\\d)");

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{4}-\\d{1,2}-\\d{1,2}"
                    + "|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}"
                    + "|\\d{1,2}\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+\\d{2,4}"
                    + "|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{2,4})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));

    private static final List<String> TOTAL_KEYWORDS = List.of(
            "grand total", "net amount", "amount payable", "total amount",
            "bill amount", "total payable", "net payable", "amount paid",
            "total", "payable", "paid");

    private static final List<String> HEADER_JUNK = List.of(
            "tax invoice", "invoice", "receipt", "bank statement",
            "account statement", "transaction history", "statement of account",
            "gst invoice", "cash memo");

    /**
     * Parses raw document text into one editable transaction DTO.
     */
    public ExtractedTransactionDTO parse(String rawText, String fileType) {
        String text = rawText == null ? "" : rawText;
        return createDto(text, text, fileType);
    }

    public List<ExtractedTransactionDTO> parseRows(String rawText, String fileType) {
        String text = rawText == null ? "" : rawText;
        if (text.isBlank()) {
            return List.of(createDto(text, text, fileType));
        }

        List<ExtractedTransactionDTO> rows = new ArrayList<>();
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.replaceAll("\\s+", " ").trim();
            if (line.isBlank() || !DATE_PATTERN.matcher(line).find() || findAmounts(line).isEmpty()) {
                continue;
            }
            rows.add(createDto(text, line, fileType));
        }
        return rows.isEmpty() ? List.of(createDto(text, text, fileType)) : rows;
    }

    private ExtractedTransactionDTO createDto(String rawText, String rowText, String fileType) {
        ExtractedTransactionDTO dto = new ExtractedTransactionDTO();
        dto.setFileType(fileType == null ? "UNKNOWN" : fileType);
        dto.setRawText(rawText);

        if (rawText.isBlank()) {
            dto.addWarning("Unable to extract transaction details. Please enter the details manually.");
            dto.setTransactionType("EXPENSE");
            dto.setPaymentMethod("Other");
            dto.setCategory("Other");
            return dto;
        }

        String merchant = extractMerchant(rowText);
        BigDecimal amount = extractAmount(rowText);
        LocalDate date = extractDate(rowText);
        String paymentMethod = extractPaymentMethod(rowText);
        String transactionType = extractTransactionType(rowText);
        String category = safeCategory(merchant, rowText, amount, transactionType);

        dto.setMerchant(merchant);
        dto.setDescription(merchant == null || merchant.isBlank() ? "" : merchant);
        dto.setAmount(amount);
        dto.setDate(date);
        dto.setPaymentMethod(paymentMethod);
        dto.setTransactionType(transactionType);
        dto.setCategory(category);

        if (merchant == null || merchant.isBlank()) {
            dto.addWarning("Merchant could not be identified. Please enter it manually.");
        }
        if (amount == null) {
            dto.addWarning("Amount could not be identified. Please enter it manually.");
        }
        if (date == null) {
            dto.setDate(LocalDate.now());
            dto.addWarning("Date could not be identified, defaulted to today. Please verify.");
        }
        return dto;
    }

    // ------------------------------------------------------------------
    // Field extractors
    // ------------------------------------------------------------------

    private String extractMerchant(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.replaceAll("\\s+", " ").trim();
            if (line.length() < 2 || !line.matches(".*[A-Za-z].*")) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            boolean isJunkHeader = HEADER_JUNK.stream().anyMatch(value -> value.equals(lower));
            if (isJunkHeader) {
                continue;
            }
            // Skip lines that are only a date and/or an amount.
            String stripped = DATE_PATTERN.matcher(line).replaceAll("")
                    .replaceAll("[₹$€£,.\\d\\s:/-]", "").trim();
            if (stripped.length() < 2) {
                continue;
            }
            // Remove embedded amounts/dates from the merchant line.
            String cleaned = DATE_PATTERN.matcher(line).replaceAll(" ")
                    .replaceAll("(?i)(?:₹|rs\\.?|inr|\\$|€|£)\\s*[\\d,]+(?:\\.\\d{1,2})?", " ")
                    .replaceAll("(?i)\\b(?:income|expense)\\b", " ")
                    .replaceAll("\\s+", " ").trim();
            if (cleaned.length() > 120) {
                cleaned = cleaned.substring(0, 120).trim();
            }
            return cleaned.isEmpty() ? null : cleaned;
        }
        return null;
    }

    private BigDecimal extractAmount(String text) {
        List<BigDecimal> totalLineAmounts = new ArrayList<>();
        List<BigDecimal> allAmounts = new ArrayList<>();

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<BigDecimal> lineAmounts = findAmounts(line);
            allAmounts.addAll(lineAmounts);
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lineAmounts.isEmpty()
                    && TOTAL_KEYWORDS.stream().anyMatch(value -> value.contains(lower))) {
                totalLineAmounts.addAll(lineAmounts);
            }
        }

        List<BigDecimal> candidates = totalLineAmounts.isEmpty() ? allAmounts : totalLineAmounts;
        return candidates.stream()
                .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.nullsLast(Comparator.naturalOrder()))
                .orElse(null);
    }

    private List<BigDecimal> findAmounts(String line) {
        List<BigDecimal> amounts = new ArrayList<>();
        // Strip dates first so fragments like "2026" are never treated as money.
        String noDates = DATE_PATTERN.matcher(line).replaceAll(" ");

        Matcher currency = CURRENCY_AMOUNT_PATTERN.matcher(noDates);
        while (currency.find()) {
            addAmount(amounts, currency.group(1) != null ? currency.group(1) : currency.group(2));
        }

        String noCurrency = CURRENCY_AMOUNT_PATTERN.matcher(noDates).replaceAll(" ");
        Matcher decimal = DECIMAL_AMOUNT_PATTERN.matcher(noCurrency);
        while (decimal.find()) {
            addAmount(amounts, decimal.group(1));
        }

        String lower = line.toLowerCase(Locale.ROOT);
        if (TOTAL_KEYWORDS.stream().anyMatch(value -> value.contains(lower))) {
            String leftovers = DECIMAL_AMOUNT_PATTERN.matcher(noCurrency).replaceAll(" ");
            Matcher bare = BARE_AMOUNT_PATTERN.matcher(leftovers);
            while (bare.find()) {
                addAmount(amounts, bare.group(1));
            }
        }
        return amounts;
    }

    private void addAmount(List<BigDecimal> amounts, String raw) {
        BigDecimal value = parseIndianAmount(raw);
        if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
            amounts.add(value);
        }
    }

    private BigDecimal parseIndianAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Indian grouping (1,00,000) and western grouping (100,000) both reduce
        // to plain digits once commas are removed.
        String normalized = raw.replace(",", "").trim();
        if (!normalized.matches("\\d+(\\.\\d{1,2})?")) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            LocalDate parsed = tryParseDate(matcher.group(1));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDate tryParseDate(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ").replace('/', '-');
        // Normalise 2-digit years to 20xx.
        Matcher shortYear = Pattern.compile("^(\\d{1,2}-\\d{1,2}-)(\\d{2})$").matcher(cleaned);
        if (shortYear.matches()) {
            cleaned = shortYear.group(1) + "20" + shortYear.group(2);
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(cleaned, formatter.withLocale(Locale.ENGLISH));
                if (date.isAfter(LocalDate.now().plusDays(1))) {
                    continue; // Likely a mis-parse (e.g. MM-dd vs dd-MM); keep looking.
                }
                return date;
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }
        // Last resort: try day/month swapped for numeric dates.
        Matcher numeric = Pattern.compile("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").matcher(cleaned);
        if (numeric.matches()) {
            try {
                LocalDate swapped = LocalDate.of(
                        Integer.parseInt(numeric.group(3)),
                        Integer.parseInt(numeric.group(1)),
                        Integer.parseInt(numeric.group(2)));
                if (!swapped.isAfter(LocalDate.now().plusDays(1))) {
                    return swapped;
                }
            } catch (Exception ignored) {
                // Fall through to null.
            }
        }
        return null;
    }

    private String extractPaymentMethod(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("upi")) {
            return "UPI";
        }
        if (lower.contains("net banking") || lower.contains("netbanking")
                || lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs")) {
            return "Net Banking";
        }
        if (lower.contains("credit card") || lower.contains("debit card")
                || lower.matches("(?s).*card\\s*(ending|no\\.?|number).*\\d{4}.*")
                || lower.contains("visa") || lower.contains("mastercard") || lower.contains("rupay")) {
            return "Card";
        }
        if (lower.contains("wallet") || lower.contains("paytm wallet") || lower.contains("phonepe wallet")) {
            return "Wallet";
        }
        if (lower.contains("cheque") || lower.contains("check no")) {
            return "Cheque";
        }
        if (lower.contains("cash")) {
            return "Cash";
        }
        return "Other";
    }

    private String extractTransactionType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("salary") || lower.contains("payroll")
                || lower.contains("interest credited") || lower.contains("deposit")
                || lower.contains("refund") || lower.contains("cashback")
                || lower.contains("credit") && !lower.contains("credit card")) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String safeCategory(String merchant, String text, BigDecimal amount, String transactionType) {
        try {
            String category = categoryEngine.categorize(text, merchant, amount, transactionType);
            return category == null || category.isBlank() ? "Other" : category;
        } catch (Exception e) {
            return "Other";
        }
    }
}
