package com.finance.service;

import com.finance.financeplus.model.FinancePlusAttachment;
import com.finance.financeplus.model.FinancePlusBackup;
import com.finance.financeplus.model.FinancePlusNotification;
import com.finance.financeplus.model.FinancePlusRecurring;
import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.financeplus.repository.FinancePlusAttachmentRepository;
import com.finance.financeplus.repository.FinancePlusBackupRepository;
import com.finance.financeplus.repository.FinancePlusNotificationRepository;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.financeplus.repository.FinancePlusSettingsRepository;
import com.finance.model.entity.ChatMessage;
import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import com.finance.repository.ChatMessageRepository;
import com.finance.service.excel.CategoryExcelService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AdminUserExportService {
    private static final String BACKUP_PATTERN =
            "finplus_user_\\d+_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}\\.fpbackup";

    private final UserDataExportService userDataExportService;
    private final ExpenseService expenseService;
    private final ImportFileService importFileService;
    private final CategoryExcelService categoryExcelService;
    private final ChatMessageRepository chatMessageRepository;
    private final FinancePlusAttachmentRepository attachmentRepository;
    private final FinancePlusBackupRepository backupRepository;
    private final FinancePlusNotificationRepository notificationRepository;
    private final FinancePlusRecurringRepository recurringRepository;
    private final FinancePlusSettingsRepository settingsRepository;
    private final Path importImageDirectory;
    private final Path backupDirectory;
    private final Path profileImageDirectory;

    public AdminUserExportService(UserDataExportService userDataExportService,
                                  ExpenseService expenseService,
                                  ImportFileService importFileService,
                                  CategoryExcelService categoryExcelService,
                                  ChatMessageRepository chatMessageRepository,
                                  FinancePlusAttachmentRepository attachmentRepository,
                                  FinancePlusBackupRepository backupRepository,
                                  FinancePlusNotificationRepository notificationRepository,
                                  FinancePlusRecurringRepository recurringRepository,
                                  FinancePlusSettingsRepository settingsRepository,
                                  @Value("${app.import-image-dir:data/import-images}") String importImageDirectory) {
        this.userDataExportService = userDataExportService;
        this.expenseService = expenseService;
        this.importFileService = importFileService;
        this.categoryExcelService = categoryExcelService;
        this.chatMessageRepository = chatMessageRepository;
        this.attachmentRepository = attachmentRepository;
        this.backupRepository = backupRepository;
        this.notificationRepository = notificationRepository;
        this.recurringRepository = recurringRepository;
        this.settingsRepository = settingsRepository;
        this.importImageDirectory = Path.of(importImageDirectory).toAbsolutePath().normalize();
        this.backupDirectory = Path.of("data", "finance-plus", "backups").toAbsolutePath().normalize();
        this.profileImageDirectory = Path.of("data", "uploads", "profile").toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(User user) {
        try (Workbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(userDataExportService.exportExcel(user)));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addChatHistory(workbook, chatMessageRepository.findByUserOrderByCreatedAtAsc(user));
            addCategories(workbook, categoryExcelService.getUserCategories(user, null));
            addMetadata(workbook);
            addFinancePlusRecurring(workbook, recurringRepository.findByUserOrderByNextDueDateAsc(user));
            addFinancePlusNotifications(workbook, notificationRepository.findByUserOrderByCreatedAtDesc(user));
            addFinancePlusSettings(workbook, settingsRepository.findByUser(user).orElse(null));
            addFinancePlusAttachments(workbook, attachmentRepository.findByUserOrderByCreatedAtDesc(user));
            addFinancePlusBackups(workbook, backupRepository.findByUserOrderByCreatedAtDesc(user));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create admin Excel export", exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportZip(User user) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            addBytes(zip, "finwise-data.xlsx", exportExcel(user));
            addProfileImage(zip, user);
            addExpenseReceipts(zip, expenseService.getUserExpenses(user));
            addImportImages(zip, importFileService.getImportedTransactions(user));
            addFinancePlusAttachments(zip, attachmentRepository.findByUserOrderByCreatedAtDesc(user));
            addFinancePlusBackups(zip, backupRepository.findByUserOrderByCreatedAtDesc(user));
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create admin ZIP export", exception);
        }
    }

    private void addChatHistory(Workbook workbook, List<ChatMessage> messages) {
        Sheet sheet = workbook.createSheet("Chat History");
        header(sheet, "ID", "Role", "Message", "Created");
        for (ChatMessage message : messages) {
            row(sheet, message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
        }
        autoSize(sheet, 4);
    }

    private void addCategories(Workbook workbook, List<CategoryExcelService.CategoryRecord> categories) {
        Sheet sheet = workbook.createSheet("Categories");
        header(sheet, "ID", "Name", "Type", "Parent category ID", "Icon", "Color", "Default",
                "Sort order", "Created", "Updated");
        for (CategoryExcelService.CategoryRecord category : categories) {
            row(sheet, category.getCategoryId(), category.getName(), category.getType(),
                    category.getParentCategoryId(), category.getIcon(), category.getColor(),
                    category.isDefault(), category.getSortOrder(), category.getCreatedAt(), category.getUpdatedAt());
        }
        autoSize(sheet, 10);
    }

    private void addMetadata(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Export Contents");
        String[] descriptions = {
                "Profile: username, name, email, created date, preferences",
                "Accounts: account details, balances, institution, notes, active status",
                "Income: every income record, source, recurrence, notes and dates",
                "Expenses: every expense record, recurrence, notes, and receipt indicator",
                "Budgets: category, month, limit, spending, status and notes",
                "Goals: targets, progress, dates, status and priority",
                "Imported Transactions: parsed data, source file details and extracted text",
                "Categories: shared defaults and this user's custom categories",
                "Chat History: this user's saved assistant messages",
                "Finance Plus recurring rules, notifications, preferences and attachment metadata",
                "Finance Plus backups: metadata; encrypted backup files are included in ZIP",
                "ZIP additionally contains available profile images, receipts, import images and attachments",
                "Passwords, password hashes, reset tokens, app-lock hashes and vault secrets are excluded"
        };
        header(sheet, "Included user data");
        for (String description : descriptions) {
            row(sheet, description);
        }
        autoSize(sheet, 1);
    }

    private void addFinancePlusRecurring(Workbook workbook, List<FinancePlusRecurring> entries) {
        Sheet sheet = workbook.createSheet("FP Recurring");
        header(sheet, "ID", "Type", "Description", "Amount", "Category", "Payment method", "Frequency",
                "Next due date", "Active", "Auto post", "Created", "Updated");
        for (FinancePlusRecurring entry : entries) {
            row(sheet, entry.getId(), entry.getTransactionType(), entry.getDescription(), entry.getAmount(),
                    entry.getCategory(), entry.getPaymentMethod(), entry.getFrequency(), entry.getNextDueDate(),
                    entry.getActive(), entry.getAutoPost(), entry.getCreatedAt(), entry.getUpdatedAt());
        }
        autoSize(sheet, 13);
    }

    private void addFinancePlusNotifications(Workbook workbook, List<FinancePlusNotification> notifications) {
        Sheet sheet = workbook.createSheet("FP Notifications");
        header(sheet, "ID", "Type", "Title", "Message", "Priority", "Read", "Deduplication key",
                "Created", "Read at");
        for (FinancePlusNotification notification : notifications) {
            row(sheet, notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getMessage(), notification.getPriority(), notification.getRead(),
                    notification.getDeduplicationKey(), notification.getCreatedAt(), notification.getReadAt());
        }
        autoSize(sheet, 10);
    }

    private void addFinancePlusSettings(Workbook workbook, FinancePlusSettings settings) {
        Sheet sheet = workbook.createSheet("FP Settings");
        header(sheet, "Bill reminders", "Reminder days", "Large expense alerts", "Large expense threshold",
                "Weekly summary", "Monthly summary", "Auto backup", "Automatic backup hour",
                "Passcode enabled", "Created", "Updated");
        if (settings != null) {
            row(sheet, settings.getBillRemindersEnabled(), settings.getBillReminderDays(),
                    settings.getLargeExpenseAlertsEnabled(), settings.getLargeExpenseThreshold(),
                    settings.getWeeklySummaryEnabled(), settings.getMonthlySummaryEnabled(),
                    settings.getAutoBackupEnabled(), settings.getAutomaticBackupHour(),
                    settings.getPasscodeEnabled(), settings.getCreatedAt(), settings.getUpdatedAt());
        }
        autoSize(sheet, 11);
    }

    private void addFinancePlusAttachments(Workbook workbook, List<FinancePlusAttachment> attachments) {
        Sheet sheet = workbook.createSheet("FP Attachments");
        header(sheet, "ID", "Transaction type", "Transaction ID", "Filename", "Content type",
                "Content length", "SHA-256", "Created");
        for (FinancePlusAttachment attachment : attachments) {
            row(sheet, attachment.getId(), attachment.getTransactionType(), attachment.getTransactionId(),
                    attachment.getOriginalFilename(), attachment.getContentType(), attachment.getContentLength(),
                    attachment.getSha256(), attachment.getCreatedAt());
        }
        autoSize(sheet, 8);
    }

    private void addFinancePlusBackups(Workbook workbook, List<FinancePlusBackup> backups) {
        Sheet sheet = workbook.createSheet("FP Backups");
        header(sheet, "ID", "Filename", "Size", "Type", "Status", "Created");
        for (FinancePlusBackup backup : backups) {
            row(sheet, backup.getId(), backup.getFileName(), backup.getFileSize(), backup.getBackupType(),
                    backup.getStatus(), backup.getCreatedAt());
        }
        autoSize(sheet, 6);
    }

    private void addProfileImage(ZipOutputStream zip, User user) throws IOException {
        String profileImage = user.getProfileImage();
        if (profileImage == null || profileImage.isBlank()) {
            return;
        }
        String filename = Path.of(profileImage).getFileName().toString();
        Path path = profileImageDirectory.resolve(filename).normalize();
        if (path.startsWith(profileImageDirectory) && Files.isRegularFile(path)) {
            addBytes(zip, "files/profile/" + safeFilename(filename), Files.readAllBytes(path));
        }
    }

    private void addExpenseReceipts(ZipOutputStream zip, List<Expense> expenses) throws IOException {
        for (Expense expense : expenses) {
            if (expense.getReceiptImage() != null && expense.getReceiptImage().length > 0) {
                addBytes(zip, "files/receipts/expense-" + expense.getId() + ".bin", expense.getReceiptImage());
            }
        }
    }

    private void addImportImages(ZipOutputStream zip, List<ImportedTransaction> imports) throws IOException {
        for (ImportedTransaction transaction : imports) {
            if (transaction.getSourceImagePath() == null || transaction.getSourceImagePath().isBlank()) {
                continue;
            }
            Path path = importImageDirectory.resolve(transaction.getSourceImagePath()).normalize();
            if (!path.startsWith(importImageDirectory) || !Files.isRegularFile(path)) {
                continue;
            }
            String originalName = safeFilename(transaction.getOriginalFilename());
            addBytes(zip, "files/imports/" + transaction.getId() + "-" + originalName, Files.readAllBytes(path));
        }
    }

    private void addFinancePlusAttachments(ZipOutputStream zip, List<FinancePlusAttachment> attachments)
            throws IOException {
        for (FinancePlusAttachment attachment : attachments) {
            addBytes(zip, "files/finance-plus/attachments/" + attachment.getId() + "-"
                    + safeFilename(attachment.getOriginalFilename()), attachment.getData());
        }
    }

    private void addFinancePlusBackups(ZipOutputStream zip, List<FinancePlusBackup> backups) throws IOException {
        for (FinancePlusBackup backup : backups) {
            String filename = backup.getFileName();
            if (filename == null || !filename.matches(BACKUP_PATTERN)) {
                continue;
            }
            Path path = backupDirectory.resolve(filename).normalize();
            if (!path.startsWith(backupDirectory) || !Files.isRegularFile(path)) {
                continue;
            }
            addBytes(zip, "files/finance-plus/backups/" + filename, Files.readAllBytes(path));
        }
    }

    private void addBytes(ZipOutputStream zip, String name, byte[] contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents);
        zip.closeEntry();
    }

    private String safeFilename(String filename) {
        String safe = filename == null ? "file" : filename.replace('\\', '_').replace('/', '_');
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            return "file";
        }
        return safe.substring(0, Math.min(100, safe.length()));
    }

    private void header(Sheet sheet, String... values) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);
        for (int index = 0; index < values.length; index++) {
            row.getCell(index).setCellStyle(style);
        }
    }

    private void row(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) {
                row.createCell(index).setCellValue(number.doubleValue());
            } else if (value instanceof Boolean bool) {
                row.createCell(index).setCellValue(bool);
            } else if (value == null) {
                row.createCell(index).setCellValue("");
            } else {
                row.createCell(index).setCellValue(value.toString());
            }
        }
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index), 255 * 256));
        }
    }
}
