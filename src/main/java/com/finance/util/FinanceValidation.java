package com.finance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class FinanceValidation {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    private FinanceValidation() {
    }

    public static String requireText(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }

        return trimmed;
    }

    public static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }

        return trimmed;
    }

    public static BigDecimal requirePositiveAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }

        if (normalized.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(fieldName + " is too large");
        }

        return normalized;
    }

    public static BigDecimal requireNonNegativeAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }

        if (normalized.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(fieldName + " is too large");
        }

        return normalized;
    }

    public static LocalDate requireDate(LocalDate value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
