package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;

import java.util.List;

public interface TransactionService {
    ImportedTransaction saveImportedTransaction(ImportedTransaction record);

    List<ImportedTransaction> saveImportedTransactions(User user, List<ImportedTransaction> records);

    List<ImportedTransaction> getUserTransactions(User user);
}
