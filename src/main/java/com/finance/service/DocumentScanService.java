package com.finance.service;

import com.finance.model.dto.ExtractedTransactionDTO;
import com.finance.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Scan &amp; Fill feature abstraction.
 *
 * <p>The web layer depends only on this interface (polymorphism); Spring
 * injects {@link LocalDocumentScanService} at runtime. Everything runs
 * locally — no cloud OCR or external API is used.</p>
 */
public interface DocumentScanService {

    /**
     * Extracts transaction information from a single uploaded document
     * (PDF bank statement/receipt, JPG/JPEG/PNG receipt).
     *
     * @param file the uploaded file (never stored publicly)
     * @param user the authenticated user (used for duplicate checking)
     * @return extracted, user-editable transaction data
     * @throws IllegalArgumentException if the file type/size is unsupported
     * @throws IllegalStateException    if no transaction details can be extracted
     */
    ExtractedTransactionDTO scanFile(MultipartFile file, User user);

    List<ExtractedTransactionDTO> scanRows(MultipartFile file, User user);

    /**
     * Persists a user-confirmed transaction through the existing
     * {@link ExpenseService} / {@link IncomeService}. No new tables are used.
     *
     * @param dto  user-reviewed transaction data
     * @param user the authenticated user
     * @return "EXPENSE" or "INCOME", depending on what was saved
     * @throws IllegalArgumentException if amount/date/merchant are invalid
     * @throws IllegalStateException    if the transaction looks like a duplicate
     */
    String saveTransaction(ExtractedTransactionDTO dto, User user);

    /**
     * Checks whether the extracted data duplicates an existing expense/income
     * of the user. Reuses the existing {@link DuplicateDetectionService} rules.
     */
    boolean isDuplicate(ExtractedTransactionDTO dto, User user);
}
