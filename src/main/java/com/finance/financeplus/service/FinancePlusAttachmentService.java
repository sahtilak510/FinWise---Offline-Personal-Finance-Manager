package com.finance.financeplus.service;

import com.finance.financeplus.model.FinancePlusAttachment;
import com.finance.financeplus.repository.FinancePlusAttachmentRepository;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class FinancePlusAttachmentService {

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private final FinancePlusAttachmentRepository attachmentRepository;
    private final FinancePlusRecurringRepository recurringRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final ImportedTransactionRepository importedTransactionRepository;

    public FinancePlusAttachmentService(FinancePlusAttachmentRepository attachmentRepository,
                                        FinancePlusRecurringRepository recurringRepository,
                                        ExpenseRepository expenseRepository,
                                        IncomeRepository incomeRepository,
                                        ImportedTransactionRepository importedTransactionRepository) {
        this.attachmentRepository = attachmentRepository;
        this.recurringRepository = recurringRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.importedTransactionRepository = importedTransactionRepository;
    }

    public List<FinancePlusAttachment> getAttachments(User user) {
        return attachmentRepository.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    public FinancePlusAttachment upload(User user, String transactionType, Long transactionId,
                                        MultipartFile file) throws IOException {
        validateFile(file);
        validateTarget(user, transactionType, transactionId);
        String originalName = safeName(file.getOriginalFilename());
        FinancePlusAttachment attachment = new FinancePlusAttachment();
        attachment.setUser(user);
        attachment.setTransactionType(transactionType.trim().toUpperCase(Locale.ROOT));
        attachment.setTransactionId(transactionId);
        attachment.setOriginalFilename(originalName);
        attachment.setContentType(file.getContentType());
        attachment.setContentLength(file.getSize());
        attachment.setData(file.getBytes());
        attachment.setSha256(sha256(file.getBytes()));
        return attachmentRepository.save(attachment);
    }

    public FinancePlusAttachment get(User user, Long id) {
        return attachmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
    }

    public void delete(User user, Long id) {
        attachmentRepository.delete(get(user, id));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Attachment must be 5 MB or smaller");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/") && !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Only image and PDF attachments are allowed");
        }
    }

    private void validateTarget(User user, String transactionType, Long transactionId) {
        String type = transactionType == null ? "" : transactionType.trim().toUpperCase(Locale.ROOT);
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction is required");
        }
        boolean exists = switch (type) {
            case "EXPENSE" -> expenseRepository.findByIdAndUser(transactionId, user).isPresent();
            case "INCOME" -> incomeRepository.findByIdAndUser(transactionId, user).isPresent();
            case "IMPORT" -> importedTransactionRepository.findByIdAndUser(transactionId, user).isPresent();
            case "RECURRING" -> recurringRepository.findByIdAndUser(transactionId, user).isPresent();
            default -> false;
        };
        if (!exists) {
            throw new IllegalArgumentException("Transaction not found");
        }
    }

    private String safeName(String originalFilename) {
        String name = originalFilename == null ? "attachment" : originalFilename.trim();
        name = name.replace('\\', '_').replace('/', '_').replace("..", "_");
        return name.isBlank() ? "attachment" : name.substring(0, Math.min(255, name.length()));
    }

    private String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
