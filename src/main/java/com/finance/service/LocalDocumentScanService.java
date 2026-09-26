package com.finance.service;

import com.finance.model.dto.ExtractedTransactionDTO;
import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.IncomeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Scan & Fill local implementation (100% offline).
 *
 * <p>Pipeline: detect file type → extract text locally (PDFBox for text PDFs,
 * Tess4J + Tesseract OCR with OpenCV preprocessing for scanned PDFs/images) →
 * {@link ScanTransactionParser} → editable {@link ExtractedTransactionDTO} →
 * existing {@link ExpenseService} / {@link IncomeService} for persistence.</p>
 */
@Service
public class LocalDocumentScanService implements DocumentScanService {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentScanService.class);

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");

    private final OpenCVPreprocessor openCVPreprocessor;
    private final Tess4JOcrService tess4JOcrService;
    private final ScanTransactionParser scanTransactionParser;
    private final DuplicateDetectionService duplicateDetectionService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    public LocalDocumentScanService(OpenCVPreprocessor openCVPreprocessor,
                                    Tess4JOcrService tess4JOcrService,
                                    ScanTransactionParser scanTransactionParser,
                                    DuplicateDetectionService duplicateDetectionService,
                                    ExpenseService expenseService,
                                    IncomeService incomeService,
                                    ExpenseRepository expenseRepository,
                                    IncomeRepository incomeRepository) {
        this.openCVPreprocessor = openCVPreprocessor;
        this.tess4JOcrService = tess4JOcrService;
        this.scanTransactionParser = scanTransactionParser;
        this.duplicateDetectionService = duplicateDetectionService;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
    }

    @Override
    public ExtractedTransactionDTO scanFile(MultipartFile file, User user) {
        return scanRows(file, user).get(0);
    }

    @Override
    public List<ExtractedTransactionDTO> scanRows(MultipartFile file, User user) {
        validateFile(file);

        String extension = fileExtension(file.getOriginalFilename());
        String fileType = extension.equals("pdf") ? "PDF" : "IMAGE";
        String rawText = extractText(file, extension);
        List<ExtractedTransactionDTO> rows = scanTransactionParser.parseRows(rawText, fileType);
        for (ExtractedTransactionDTO row : rows) {
            row.setFileType(fileType);
            row.setRawText(rawText);
        }
        return rows;
    }

    @Override
    public String saveTransaction(ExtractedTransactionDTO dto, User user) {
        if (dto.getMerchant() == null && dto.getAmount() == null) {
            return "INCOMPLETE";
        }

        boolean isDuplicate = isDuplicate(dto, user);
        if (isDuplicate) {
            dto.setDuplicate(true);
            return "DUPLICATE";
        }

        if ("INCOME".equalsIgnoreCase(dto.getTransactionType())) {
            incomeService.addIncome(user, dto.getMerchant(), dto.getAmount(), dto.getDate(),
                    dto.getCategory(), "SCAN", null);
            dto.setSaved(true);
            return "INCOME";
        } else {
            expenseService.addExpense(user, dto.getMerchant(), dto.getAmount(), dto.getDate(),
                    dto.getCategory(), dto.getPaymentMethod(), null, null);
            dto.setSaved(true);
            return "EXPENSE";
        }
    }

    @Override
    public boolean isDuplicate(ExtractedTransactionDTO dto, User user) {
        ImportedTransaction tx = toImportedTransaction(dto, user);
        List<Expense> expenses = expenseRepository.findByUserOrderByExpenseDateDesc(user);
        List<Income> incomes = incomeRepository.findByUserOrderByIncomeDateDesc(user);
        
        for (Expense expense : expenses) {
            ImportedTransaction existingTx = new ImportedTransaction();
            existingTx.setUser(expense.getUser());
            existingTx.setTransactionDate(expense.getExpenseDate());
            existingTx.setAmount(expense.getAmount());
            existingTx.setTransactionType("EXPENSE");
            existingTx.setDescription(expense.getDescription());
            if (duplicateDetectionService.isDuplicate(tx, existingTx)) {
                return true;
            }
        }
        
        for (Income income : incomes) {
            ImportedTransaction existingTx = new ImportedTransaction();
            existingTx.setUser(income.getUser());
            existingTx.setTransactionDate(income.getIncomeDate());
            existingTx.setAmount(income.getAmount());
            existingTx.setTransactionType("INCOME");
            existingTx.setDescription(income.getDescription());
            if (duplicateDetectionService.isDuplicate(tx, existingTx)) {
                return true;
            }
        }
        return false;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }
        String extension = fileExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type: " + extension);
        }
    }

    private String fileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String extractText(MultipartFile file, String extension) {
        try {
            if (extension.equals("pdf")) {
                return extractTextFromPdf(file.getBytes());
            } else {
                byte[] imageBytes = file.getBytes();
                byte[] preprocessed = openCVPreprocessor.preprocess(imageBytes);
                String text = tess4JOcrService.extractText(preprocessed);
                if (text == null || text.isBlank()) {
                    text = tess4JOcrService.extractText(imageBytes);
                }
                return text == null ? "" : text;
            }
        } catch (Exception e) {
            log.error("Text extraction failed", e);
            return "";
        }
    }

    private String extractTextFromPdf(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && !text.isBlank()) {
                return text;
            }
        } catch (Exception e) {
            log.warn("PDF text extraction failed, trying OCR: {}", e.getMessage());
        }

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String ocrText = tess4JOcrService.extractText(toPngBytes(image));
                if (ocrText != null && !ocrText.isBlank()) {
                    sb.append(ocrText).append("\n");
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            log.warn("OCR failed ({}): {}", t.getClass().getSimpleName(), t.getMessage());
            return "";
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private ImportedTransaction toImportedTransaction(ExtractedTransactionDTO dto, User user) {
        ImportedTransaction record = new ImportedTransaction();
        record.setUser(user);
        record.setOriginalFilename("scan-fill");
        record.setFileType("SCAN");
        record.setDescription(dto.getMerchant() == null ? "" : dto.getMerchant());
        record.setAmount(dto.getAmount());
        record.setTransactionDate(dto.getDate());
        record.setTransactionType(dto.getTransactionType() == null ? "EXPENSE" : dto.getTransactionType().toUpperCase(Locale.ROOT));
        record.setCategory(dto.getCategory() == null ? "Other" : dto.getCategory());
        record.setImportStatus("PARSED");
        return record;
    }
}