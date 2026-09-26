package com.finance.service;

import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.ImportedTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class ImportCommitService {

    private static final int MAX_DESCRIPTION = 200;
    private static final int MAX_CATEGORY = 100;
    private static final int MAX_TYPE = 20;
    private static final int MAX_NOTES = 500;
    private static final String IMPORT_SOURCE = "IMPORT";
    private static final String IMPORT_PAYMENT_METHOD = "Imported";
    private static final String STATUS_DUPLICATE = "DUPLICATE";
    private static final String STATUS_REJECTED = "REJECTED";

    private final ImportFileService importFileService;
    private final ImportedTransactionRepository importRepository;
    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final DuplicateDetectionService duplicateDetectionService;

    public ImportCommitService(ImportFileService importFileService,
                               ImportedTransactionRepository importRepository,
                               IncomeService incomeService,
                               ExpenseService expenseService,
                               DuplicateDetectionService duplicateDetectionService) {
        this.importFileService = importFileService;
        this.importRepository = importRepository;
        this.incomeService = incomeService;
        this.expenseService = expenseService;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    public ImportCommitResult commit(List<Long> recordIds, User user, Map<Long, ImportEdits> edits) {
        ImportCommitResult result = new ImportCommitResult();
        if (recordIds == null || recordIds.isEmpty() || user == null) {
            return result;
        }

        List<ImportedTransaction> recorded = recordedTransactions(user);
        for (Long id : recordIds) {
            commitOne(id, user, edits == null ? null : edits.get(id), recorded, result);
        }
        return result;
    }

    private void commitOne(Long id, User user, ImportEdits edits,
                           List<ImportedTransaction> recorded, ImportCommitResult result) {
        ImportedTransaction record = importRepository.findByIdAndUser(id, user).orElse(null);
        if (record == null) {
            result.addRejected("Record " + id + " was not found");
            return;
        }
        if (ImportFileService.isCommitted(record)) {
            result.addSkipped("Record " + id + " was already imported");
            return;
        }

        ImportedTransaction cleaned;
        try {
            cleaned = importFileService.updateRecord(id, user,
                    resolve(record.getDescription(), edits, ImportEdits::description, MAX_DESCRIPTION),
                    record.getAmount(),
                    record.getTransactionDate(),
                    resolve(record.getTransactionType(), edits, ImportEdits::transactionType, MAX_TYPE),
                    resolve(record.getCategory(), edits, ImportEdits::category, MAX_CATEGORY));
        } catch (RuntimeException exception) {
            reject(record);
            result.addRejected("Record " + id + ": " + exception.getMessage());
            return;
        }

        if (STATUS_DUPLICATE.equals(cleaned.getImportStatus())) {
            result.addDuplicate("Record " + id + " duplicates an earlier import");
            return;
        }
        if (matchesRecorded(cleaned, recorded)) {
            cleaned.setImportStatus(STATUS_DUPLICATE);
            importRepository.save(cleaned);
            result.addDuplicate("Record " + id + " duplicates an existing entry");
            return;
        }

        try {
            if ("INCOME".equals(cleaned.getTransactionType())) {
                incomeService.addIncome(user, cleaned.getDescription(), cleaned.getAmount(),
                        cleaned.getTransactionDate(), cleaned.getCategory(), IMPORT_SOURCE,
                        importNotes(cleaned));
            } else {
                expenseService.addExpense(user, cleaned.getDescription(), cleaned.getAmount(),
                        cleaned.getTransactionDate(), cleaned.getCategory(), IMPORT_PAYMENT_METHOD,
                        importNotes(cleaned), (byte[]) null);
            }
        } catch (RuntimeException exception) {
            reject(record);
            result.addRejected("Record " + id + ": " + exception.getMessage());
            return;
        }

        cleaned.setImportStatus(ImportFileService.STATUS_IMPORTED);
        importRepository.save(cleaned);
        result.addImported();
    }

    private void reject(ImportedTransaction record) {
        record.setImportStatus(STATUS_REJECTED);
        importRepository.save(record);
    }

    private String importNotes(ImportedTransaction record) {
        String filename = record.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return "Imported record";
        }
        String notes = "Imported from " + filename.trim();
        return notes.length() > MAX_NOTES ? notes.substring(0, MAX_NOTES) : notes;
    }

    private boolean matchesRecorded(ImportedTransaction record, List<ImportedTransaction> recorded) {
        for (ImportedTransaction candidate : recorded) {
            if (duplicateDetectionService.isDuplicate(record, candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<ImportedTransaction> recordedTransactions(User user) {
        List<ImportedTransaction> recorded = new ArrayList<>();
        for (Expense expense : expenseService.getUserExpenses(user)) {
            recorded.add(candidate(expense.getUser(), expense.getExpenseDate(), expense.getAmount(),
                    "EXPENSE", expense.getDescription()));
        }
        for (Income income : incomeService.getUserIncome(user)) {
            recorded.add(candidate(income.getUser(), income.getIncomeDate(), income.getAmount(),
                    "INCOME", income.getDescription()));
        }
        return recorded;
    }

    private ImportedTransaction candidate(User user, LocalDate date, BigDecimal amount,
                                          String type, String description) {
        ImportedTransaction candidate = new ImportedTransaction();
        candidate.setUser(user);
        candidate.setTransactionDate(date);
        candidate.setAmount(amount);
        candidate.setTransactionType(type);
        candidate.setDescription(description);
        return candidate;
    }

    private String resolve(String current, ImportEdits edits,
                           Function<ImportEdits, String> field, int maxLength) {
        String value = edits == null ? null : field.apply(edits);
        if (value == null || value.isBlank()) {
            value = current == null ? "" : current.trim();
        }
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        return value;
    }

    public record ImportEdits(String description, String transactionType, String category) {
    }

    public static final class ImportCommitResult {
        private final List<String> messages = new ArrayList<>();
        private int imported;
        private int duplicates;
        private int rejected;
        private int skipped;

        void addImported() {
            imported++;
        }

        void addDuplicate(String message) {
            duplicates++;
            messages.add(message);
        }

        void addRejected(String message) {
            rejected++;
            messages.add(message);
        }

        void addSkipped(String message) {
            skipped++;
            messages.add(message);
        }

        public int importedCount() {
            return imported;
        }

        public int duplicateCount() {
            return duplicates;
        }

        public int rejectedCount() {
            return rejected;
        }

        public int skippedCount() {
            return skipped;
        }

        public List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
