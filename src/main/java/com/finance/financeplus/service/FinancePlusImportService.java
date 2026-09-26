package com.finance.financeplus.service;

import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import com.finance.service.ImportFileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class FinancePlusImportService {

    private final ImportFileService importFileService;
    private final ImportedTransactionRepository importedTransactionRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    public FinancePlusImportService(ImportFileService importFileService,
                                    ImportedTransactionRepository importedTransactionRepository,
                                    ExpenseRepository expenseRepository,
                                    IncomeRepository incomeRepository) {
        this.importFileService = importFileService;
        this.importedTransactionRepository = importedTransactionRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
    }

    public ValidationResult importAndValidate(MultipartFile file, User user) throws Exception {
        List<ImportedTransaction> imported = importFileService.processFile(file, user);
        Set<String> existing = new HashSet<>();
        importedTransactionRepository.findByUserOrderByTransactionDateDesc(user).stream()
                .filter(record -> imported.stream().noneMatch(item -> item.getId().equals(record.getId())))
                .forEach(record -> existing.add(signature(record.getTransactionDate(), record.getAmount(),
                        record.getTransactionType(), record.getDescription())));
        expenseRepository.findByUserOrderByExpenseDateDesc(user).forEach(expense ->
                existing.add(signature(expense.getExpenseDate(), expense.getAmount(), "EXPENSE", expense.getDescription())));
        incomeRepository.findByUserOrderByIncomeDateDesc(user).forEach(income ->
                existing.add(signature(income.getIncomeDate(), income.getAmount(), "INCOME", income.getDescription())));

        int valid = 0;
        int duplicates = 0;
        int rejected = 0;
        for (ImportedTransaction record : imported) {
            String error = validate(record);
            if (error != null) {
                record.setImportStatus("REJECTED");
                record.setCategory("Rejected");
                rejected++;
            } else {
                clean(record);
                String key = signature(record.getTransactionDate(), record.getAmount(),
                        record.getTransactionType(), record.getDescription());
                if (existing.contains(key)) {
                    record.setImportStatus("DUPLICATE");
                    duplicates++;
                } else {
                    record.setImportStatus("VALIDATED");
                    existing.add(key);
                    valid++;
                }
            }
            importedTransactionRepository.save(record);
        }
        return new ValidationResult(imported.size(), valid, duplicates, rejected);
    }

    private void clean(ImportedTransaction record) {
        record.setDescription(record.getDescription().trim());
        record.setTransactionType(record.getTransactionType().trim().toUpperCase(Locale.ROOT));
        record.setCategory(record.getCategory() == null || record.getCategory().isBlank()
                ? "Uncategorized" : record.getCategory().trim());
        record.setAmount(record.getAmount().abs());
        if (record.getTransactionDate() == null) {
            record.setTransactionDate(LocalDate.now());
        }
    }

    private String validate(ImportedTransaction record) {
        if (record.getDescription() == null || record.getDescription().isBlank()) {
            return "Description is required";
        }
        if (record.getAmount() == null || record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Amount must be greater than zero";
        }
        if (record.getTransactionDate() == null || record.getTransactionDate().isAfter(LocalDate.now().plusDays(1))) {
            return "Transaction date is invalid";
        }
        String type = record.getTransactionType() == null ? "" : record.getTransactionType().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("INCOME") && !type.equals("EXPENSE")) {
            return "Transaction type must be INCOME or EXPENSE";
        }
        return null;
    }

    private String signature(LocalDate date, BigDecimal amount, String type, String description) {
        String normalizedType = type == null ? "EXPENSE" : type.trim().toUpperCase(Locale.ROOT);
        String normalizedText = description == null ? "" : description.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return date + "|" + amount.abs().setScale(2, java.math.RoundingMode.HALF_UP) + "|"
                + normalizedType + "|" + normalizedText;
    }

    public record ValidationResult(int total, int valid, int duplicates, int rejected) {}
}
