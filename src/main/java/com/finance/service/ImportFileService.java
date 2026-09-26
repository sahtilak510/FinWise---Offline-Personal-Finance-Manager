package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import com.finance.repository.ImportedTransactionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImportFileService {

    public static final String STATUS_IMPORTED = "IMPORTED";

    private final ImportedTransactionRepository importRepository;
    private final DocumentUploadService documentUploadService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final CategoryEngine categoryEngine;

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{2,4}[-/]\\d{1,2}[-/]\\d{1,2})\\b");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(-)?\\d+[.,]?\\d+");

    @Value("${app.import-image-dir:data/import-images}")
    private String importImageDirectory;

    public ImportFileService(ImportedTransactionRepository importRepository,
                             DocumentUploadService documentUploadService,
                             DuplicateDetectionService duplicateDetectionService,
                             CategoryEngine categoryEngine) {
        this.importRepository = importRepository;
        this.documentUploadService = documentUploadService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.categoryEngine = categoryEngine;
    }

    public List<ImportedTransaction> processFile(MultipartFile file, User user) throws Exception {
        if (file == null || file.isEmpty()) {
            return new ArrayList<>();
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = getFileType(originalFilename);
        List<ImportedTransaction> records = new ArrayList<>();

        try {
            switch (fileType) {
                case "IMAGE":
                case "PDF":
                case "XLSX":
                case "XLS":
                case "CSV":
                    records = documentUploadService.processFile(file, user);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported file type: " + fileType);
            }

            if (records == null) {
                records = new ArrayList<>();
            }

            String sourceImagePath = "IMAGE".equals(fileType) && !records.isEmpty()
                    ? saveImage(file, user)
                    : null;

            for (ImportedTransaction record : records) {
                if (record.getCategory() == null || record.getCategory().isBlank()) {
                    record.setCategory(categoryEngine.categorize(record));
                }
                record.setFileType(fileType);
                record.setOriginalFilename(originalFilename);
                record.setSourceImagePath(sourceImagePath);
            }

            List<ImportedTransaction> existingRecords = importRepository.findByUserOrderByTransactionDateDesc(user);
            List<ImportedTransaction> duplicateCandidates = new ArrayList<>();
            if (existingRecords != null) {
                duplicateCandidates.addAll(existingRecords);
            }
            duplicateCandidates.addAll(records);
            List<ImportedTransaction> duplicates = duplicateDetectionService.detectDuplicates(duplicateCandidates);

            for (ImportedTransaction record : records) {
                record.setImportStatus(duplicates.contains(record) ? "DUPLICATE" : "PARSED");
            }
            importRepository.saveAll(records);

        } catch (Exception e) {
            throw new RuntimeException("Error processing file: " + e.getMessage(), e);
        }

        return records;
    }

    private String getFileType(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("A file name is required");
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "IMAGE";
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".xlsx")) return "XLSX";
        if (lower.endsWith(".xls")) return "XLS";
        if (lower.endsWith(".csv")) return "CSV";
        throw new IllegalArgumentException("Unsupported file type: " + originalFilename);
    }

    private String saveImage(MultipartFile file, User user) throws IOException {
        String extension = getImageExtension(file.getOriginalFilename());
        String userPrefix = user.getId() == null ? "user" : user.getId().toString();
        String filename = userPrefix + "_" + UUID.randomUUID() + extension;

        Path imageDirectory = importImagePath();
        Files.createDirectories(imageDirectory);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, imageDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        }
        return filename;
    }

    private String getImageExtension(String originalFilename) {
        String lower = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg")) return ".jpg";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        if (lower.endsWith(".png")) return ".png";
        return ".png";
    }

    private Optional<Path> resolveImportImagePath(String sourceImagePath) {
        if (sourceImagePath == null || sourceImagePath.isBlank()) {
            return Optional.empty();
        }

        Path imageDirectory = importImagePath();
        Path imagePath = imageDirectory.resolve(sourceImagePath).normalize();
        if (!imagePath.startsWith(imageDirectory) || !Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }
        return Optional.of(imagePath);
    }

    private Path importImagePath() {
        String configuredDirectory = importImageDirectory == null || importImageDirectory.isBlank()
                ? "data/import-images"
                : importImageDirectory;
        return Paths.get(configuredDirectory).toAbsolutePath().normalize();
    }

    public Optional<Path> getImportedImagePath(Long id, User user) {
        return importRepository.findByIdAndUser(id, user)
                .map(ImportedTransaction::getSourceImagePath)
                .flatMap(this::resolveImportImagePath);
    }

    private List<ImportedTransaction> parsePdf(InputStream inputStream, String originalFilename, User user) throws Exception {
        List<ImportedTransaction> records = new ArrayList<>();
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            records = extractTransactionsFromText(text, originalFilename, user);
        }
        return records;
    }

    private List<ImportedTransaction> parseExcel(InputStream inputStream, String originalFilename, User user) throws Exception {
        List<ImportedTransaction> records = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // skip header
                cellsToTransaction(row, originalFilename, user, records);
            }
        }
        return records;
    }

    private List<ImportedTransaction> parseCsv(InputStream inputStream, String originalFilename, User user) throws Exception {
        List<ImportedTransaction> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // skip header
                }
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                // Trim quotes and spaces
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim().replaceAll("^\"|\"$", "");
                }
                cellsToTransactionCsv(parts, originalFilename, user, records);
            }
        }
        return records;
    }

    private void cellsToTransaction(Row row, String originalFilename, User user, List<ImportedTransaction> records) {
        Cell dateCell = row.getCell(0);
        Cell descCell = row.getCell(1);
        Cell typeCell = row.getCell(2);
        Cell amountCell = row.getCell(3);
        Cell categoryCell = row.getCell(4);

        ImportedTransaction record = new ImportedTransaction();
        record.setUser(user);
        record.setOriginalFilename(originalFilename);

        String date = getCellString(dateCell);
        record.setTransactionDate(parseDate(date));

        String description = getCellString(descCell);
        record.setDescription(description);

        String transactionType = getCellString(typeCell);
        record.setTransactionType(normalizeTransactionType(transactionType));

        String amount = getCellString(amountCell);
        record.setAmount(parseAmount(amount));

        String category = getCellString(categoryCell);
        record.setCategory(category != null && !category.isEmpty() ? category : "Uncategorized");

        record.setImportStatus("PARSED");
        records.add(record);
    }

    private void cellsToTransactionCsv(String[] line, String originalFilename, User user, List<ImportedTransaction> records) {
        if (line.length < 4) return;

        ImportedTransaction record = new ImportedTransaction();
        record.setUser(user);
        record.setOriginalFilename(originalFilename);

        String date = line[0];
        record.setTransactionDate(parseDate(date));

        String description = line[1];
        record.setDescription(description.isEmpty() ? "Unnamed transaction" : description);

        String transactionType = normalizeTransactionType(line[2]);
        record.setTransactionType(transactionType);

        BigDecimal amount = parseAmount(line[3]);
        record.setAmount(amount);

        String category = line.length > 4 ? line[4] : "";
        record.setCategory(category.isEmpty() ? "Uncategorized" : category);

        record.setImportStatus("PARSED");
        records.add(record);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue()).trim();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue()).trim();
            default:
                return "";
        }
    }

    private List<ImportedTransaction> extractTransactionsFromText(String text, String originalFilename, User user) {
        List<ImportedTransaction> records = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return records;
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // Simple heuristic: look for amount and date in line
            Matcher amountMatcher = AMOUNT_PATTERN.matcher(line);
            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            if (amountMatcher.find() && dateMatcher.find()) {
                try {
                    ImportedTransaction record = new ImportedTransaction();
                    record.setUser(user);
                    record.setOriginalFilename(originalFilename);
                    record.setDescription(line.length() > 100 ? line.substring(0, 100) : line);
                    record.setTransactionDate(parseDate(dateMatcher.group(1)));
                    record.setAmount(parseAmount(amountMatcher.group()));
                    record.setTransactionType(line.toLowerCase().contains("credit") || line.toLowerCase().contains("income") ? "INCOME" : "EXPENSE");
                    record.setCategory("Uncategorized");
                    record.setImportStatus("PARSED");
                    records.add(record);
                } catch (Exception ignored) {
                }
            }
        }
        return records;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }
        String trimmed = dateStr.trim();

        // Try common formats
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd MMM yyyy"),
                DateTimeFormatter.ofPattern("MMM dd, yyyy")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return formatter.parse(trimmed, temporal -> LocalDate.from(temporal));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }

        // Try regex-based extraction
        Matcher matcher = DATE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String foundDate = matcher.group(1);
            // Normalize 2-digit year to 4-digit
            if (foundDate.matches("\\d{2}[-/]\\d{2}[-/]\\d{2}")) {
                foundDate = "20" + foundDate;
            } else if (foundDate.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2}")) {
                // ambiguous, assume yyyy-mm-dd if first part > 12, otherwise dd-mm-yyyy
                String[] parts = foundDate.split("[-/]");
                if (Integer.parseInt(parts[0]) > 12) {
                    // looks like yyyy-mm-dd
                    foundDate = foundDate;
                } else {
                    // looks like dd-mm-yyyy or mm-dd-yyyy, reformat
                    foundDate = parts[2] + "-" + parts[1] + "-" + parts[0];
                }
            }
            try {
                return LocalDate.parse(foundDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }

        return LocalDate.now();
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        String cleaned = amountStr.trim();
        // Remove currency symbols / codes (Rs, INR, $, etc.) and spaces
        cleaned = cleaned.replaceAll("(?i)(rs\\.?|inr|usd|\\$|€|£|₹)", "");
        cleaned = cleaned.replaceAll("\\s+", "");
        // Handle negative amounts in parentheses: (1,299.00) -> -1299.00
        boolean negative = false;
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            negative = true;
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        // Remove thousand separators (commas). Assume dot is decimal separator (Indian/US format).
        cleaned = cleaned.replaceAll(",", "");
        // Keep only digits, dot and minus
        cleaned = cleaned.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("-")) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String normalizeTransactionType(String type) {
        if (type == null) return "EXPENSE";
        String t = type.trim().toUpperCase();
        // Exact match only — never use contains("IN") (matches PRINT/SINGLE/ORIGIN)
        if (t.equals("INCOME") || t.equals("IN") || t.equals("CREDIT")
                || t.equals("CR") || t.equals("DEPOSIT") || t.equals("RECEIVED")) return "INCOME";
        return "EXPENSE";
    }

    public List<ImportedTransaction> getImportedTransactions(User user) {
        return importRepository.findByUserOrderByTransactionDateDesc(user);
    }

    public static boolean isCommitted(ImportedTransaction record) {
        String status = record == null ? null : record.getImportStatus();
        return status != null && STATUS_IMPORTED.equalsIgnoreCase(status.trim());
    }

    public List<ImportedTransaction> getPendingImportedTransactions(User user) {
        List<ImportedTransaction> records = importRepository.findByUserOrderByTransactionDateDesc(user);
        if (records == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(records.stream().filter(record -> !isCommitted(record)).toList());
    }

    public Optional<ImportedTransaction> getImportedTransactionById(Long id) {
        return importRepository.findById(id);
    }

    public Optional<ImportedTransaction> getImportedTransactionById(Long id, User user) {
        return importRepository.findByIdAndUser(id, user);
    }

    public void validateRecords(List<Long> recordIds, User user) {
        List<String> errors = new ArrayList<>();
        for (Long id : recordIds) {
            ImportedTransaction record = importRepository.findByIdAndUser(id, user)
                    .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
            try {
                validateAndClean(record);
                if (isDuplicateOfExistingRecord(record, user)) {
                    record.setImportStatus("DUPLICATE");
                    errors.add("Record " + id + " duplicates an earlier import");
                } else {
                    record.setImportStatus("VALIDATED");
                }
            } catch (IllegalArgumentException e) {
                record.setImportStatus("REJECTED");
                errors.add("Record " + id + ": " + e.getMessage());
            }
            importRepository.save(record);
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public ImportedTransaction updateRecord(Long id, User user, String description, BigDecimal amount,
                                            LocalDate transactionDate, String transactionType, String category) {
        ImportedTransaction record = importRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        if (isCommitted(record)) {
            throw new IllegalArgumentException("Record was already imported: " + id);
        }
        record.setDescription(description);
        record.setAmount(amount);
        record.setTransactionDate(transactionDate);
        record.setTransactionType(transactionType);
        record.setCategory(category);
        validateAndClean(record);
        record.setImportStatus(isDuplicateOfExistingRecord(record, user) ? "DUPLICATE" : "VALIDATED");
        return importRepository.save(record);
    }

    private void validateAndClean(ImportedTransaction record) {
        if (record == null) {
            throw new IllegalArgumentException("Record is required");
        }
        if (record.getUser() == null || record.getUser().getId() == null) {
            throw new IllegalArgumentException("User is required");
        }
        String description = record.getDescription() == null ? "" : record.getDescription().trim().replaceAll("\\s+", " ");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (description.length() > 500) {
            throw new IllegalArgumentException("Description must be 500 characters or fewer");
        }
        if (record.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }
        String type = record.getTransactionType() == null ? "" : record.getTransactionType().trim().toUpperCase(Locale.ROOT);
        if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) {
            throw new IllegalArgumentException("Transaction type must be INCOME or EXPENSE");
        }
        if (record.getAmount() == null || record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        String category = record.getCategory() == null ? "" : record.getCategory().trim();
        if (category.isBlank()) {
            category = categoryEngine.categorize(record);
        }
        if (category == null || category.isBlank()) {
            category = "Uncategorized";
        }
        if (category.length() > 100) {
            throw new IllegalArgumentException("Category must be 100 characters or fewer");
        }
        record.setDescription(description);
        record.setTransactionType(type);
        record.setAmount(record.getAmount().abs().setScale(2, RoundingMode.HALF_UP));
        record.setCategory(category);
    }

    private boolean isDuplicateOfExistingRecord(ImportedTransaction record, User user) {
        List<ImportedTransaction> existingRecords = importRepository.findByUserOrderByTransactionDateDesc(user);
        if (existingRecords == null) {
            return false;
        }
        return existingRecords.stream()
                .filter(ImportFileService::isCommitted)
                .filter(candidate -> !Objects.equals(candidate.getId(), record.getId()))
                .anyMatch(candidate -> duplicateDetectionService.isDuplicate(record, candidate));
    }

    public ImportedTransaction saveImportedTransaction(ImportedTransaction record) {
        return importRepository.save(record);
    }

    public void deleteImportedTransaction(Long id, User user) {
        ImportedTransaction record = importRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Record not found: " + id));
        String sourceImagePath = record.getSourceImagePath();
        importRepository.delete(record);

        if (sourceImagePath != null
                && !sourceImagePath.isBlank()
                && !importRepository.existsBySourceImagePathAndUserAndIdNot(sourceImagePath, user, id)) {
            try {
                Optional<Path> imagePath = resolveImportImagePath(sourceImagePath);
                if (imagePath.isPresent()) {
                    Files.deleteIfExists(imagePath.get());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void deleteImportedTransaction(Long id) {
        importRepository.deleteById(id);
    }
}