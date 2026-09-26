package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import com.finance.repository.ImportedTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LocalTransactionService implements TransactionService {

    private final ImportedTransactionRepository importedTransactionRepository;

    public LocalTransactionService(ImportedTransactionRepository importedTransactionRepository) {
        this.importedTransactionRepository = importedTransactionRepository;
    }

    @Override
    public ImportedTransaction saveImportedTransaction(ImportedTransaction record) {
        if (record == null) {
            throw new IllegalArgumentException("Transaction record must not be null");
        }
        return importedTransactionRepository.save(record);
    }

    @Override
    public List<ImportedTransaction> saveImportedTransactions(User user, List<ImportedTransaction> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        for (ImportedTransaction record : records) {
            if (record != null && user != null) {
                record.setUser(user);
            }
        }
        return importedTransactionRepository.saveAll(records);
    }

    @Override
    public List<ImportedTransaction> getUserTransactions(User user) {
        if (user == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(importedTransactionRepository.findByUserOrderByTransactionDateDesc(user).stream()
                .filter(record -> !ImportFileService.isCommitted(record))
                .toList());
    }
}