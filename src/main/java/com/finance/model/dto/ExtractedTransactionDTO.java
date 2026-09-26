package com.finance.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Scan & Fill feature DTO.
 *
 * <p>Encapsulation: all fields are private and can only be accessed through
 * getters/setters, so the controller, parser and views cannot put the object
 * into an inconsistent state without going through its API.</p>
 *
 * <p>This DTO is intentionally NOT a JPA entity: nothing new is stored in the
 * database. After the user confirms, the data is persisted through the
 * existing {@code ExpenseService} / {@code IncomeService}.</p>
 */
@Data
@Builder
@AllArgsConstructor
public class ExtractedTransactionDTO {

    private String merchant;
    private String description;
    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String paymentMethod;
    private String transactionType;
    private String category;
    private String rawText;
    private String fileType;
    private boolean duplicate;
    private boolean saved;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public ExtractedTransactionDTO() {
        this.warnings = new ArrayList<>();
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            this.warnings.add(warning);
        }
    }

    /**
     * Returns {@code true} when every mandatory field was extracted, so the
     * caller can decide whether the result is saveable or the user must fill
     * in the gaps manually.
     */
    public boolean isComplete() {
        return merchant != null && !merchant.isBlank()
                && amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                && date != null;
    }
}
