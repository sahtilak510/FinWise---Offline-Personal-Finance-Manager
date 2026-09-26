package com.finance.service;

import com.finance.model.entity.ImportedTransaction;

import java.util.List;

public interface DuplicateDetectionService {
    List<ImportedTransaction> detectDuplicates(List<ImportedTransaction> transactions);

    boolean isDuplicate(ImportedTransaction first, ImportedTransaction second);
}
