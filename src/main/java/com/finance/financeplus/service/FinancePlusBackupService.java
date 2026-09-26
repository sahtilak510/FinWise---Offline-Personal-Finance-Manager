package com.finance.financeplus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.finance.financeplus.model.FinancePlusAttachment;
import com.finance.financeplus.model.FinancePlusBackup;
import com.finance.financeplus.model.FinancePlusRecurring;
import com.finance.financeplus.repository.FinancePlusAttachmentRepository;
import com.finance.financeplus.repository.FinancePlusBackupRepository;
import com.finance.financeplus.repository.FinancePlusNotificationRepository;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.model.entity.Account;
import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.AccountRepository;
import com.finance.repository.BudgetRepository;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.FinancialGoalRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FinancePlusBackupService {

    private static final byte[] MAGIC = "FPB2".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final long VAULT_TTL_MILLIS = 30L * 60L * 1000L;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private static final String FILE_PATTERN = "finplus_user_\\d+_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}\\.fpbackup";

    private final FinancePlusBackupRepository backupRepository;
    private final FinancePlusSettingsService settingsService;
    private final FinancePlusRecurringRepository recurringRepository;
    private final FinancePlusAttachmentRepository attachmentRepository;
    private final FinancePlusNotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final FinancialGoalRepository goalRepository;
    private final ImportedTransactionRepository importedTransactionRepository;
    private final ObjectMapper objectMapper;
    private final Path backupDirectory;
    @PersistenceContext
    private EntityManager entityManager;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Long, CachedKey> unlockedVaults = new ConcurrentHashMap<>();

    public FinancePlusBackupService(FinancePlusBackupRepository backupRepository,
                                     FinancePlusSettingsService settingsService,
                                     FinancePlusRecurringRepository recurringRepository,
                                     FinancePlusAttachmentRepository attachmentRepository,
                                     FinancePlusNotificationRepository notificationRepository,
                                     AccountRepository accountRepository,
                                     IncomeRepository incomeRepository,
                                     ExpenseRepository expenseRepository,
                                     BudgetRepository budgetRepository,
                                     FinancialGoalRepository goalRepository,
                                     ImportedTransactionRepository importedTransactionRepository,
                                     ObjectMapper objectMapper) {
        this.backupRepository = backupRepository;
        this.settingsService = settingsService;
        this.recurringRepository = recurringRepository;
        this.attachmentRepository = attachmentRepository;
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.importedTransactionRepository = importedTransactionRepository;
        this.objectMapper = objectMapper;
        this.backupDirectory = Path.of("data", "finance-plus", "backups").toAbsolutePath().normalize();
        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create Finance Plus backup directory", exception);
        }
    }

    public void unlockVault(User user, String passphrase) {
        validatePassphrase(passphrase);
        SecretKey key = deriveKey(passphrase, settingsService.getOrCreateVaultSalt(user));
        unlockedVaults.put(user.getId(), new CachedKey(key, System.currentTimeMillis() + VAULT_TTL_MILLIS));
    }

    public void lockVault(User user) {
        unlockedVaults.remove(user.getId());
    }

    public boolean isVaultUnlocked(User user) {
        CachedKey cached = unlockedVaults.get(user.getId());
        if (cached == null) {
            return false;
        }
        if (cached.expiresAt() < System.currentTimeMillis()) {
            unlockedVaults.remove(user.getId());
            return false;
        }
        return true;
    }

    public List<FinancePlusBackup> getBackups(User user) {
        return backupRepository.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    public FinancePlusBackup createBackup(User user, String passphrase, String backupType) {
        unlockVault(user, passphrase);
        CachedKey cached = unlockedVaults.get(user.getId());
        return createBackupWithKey(user, cached.key(), backupType);
    }

    public FinancePlusBackup createAutomaticBackup(User user) {
        CachedKey cached = unlockedVaults.get(user.getId());
        if (cached == null || cached.expiresAt() < System.currentTimeMillis()) {
            unlockedVaults.remove(user.getId());
            throw new IllegalStateException("Backup vault is locked");
        }
        return createBackupWithKey(user, cached.key(), "AUTOMATIC");
    }

    public FinancePlusBackup getBackupForUser(User user, Long id) {
        return getBackup(user, id);
    }

    public byte[] download(User user, Long id) {
        FinancePlusBackup backup = getBackup(user, id);
        try {
            return Files.readAllBytes(resolveFile(backup));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read backup", exception);
        }
    }

    public void delete(User user, Long id) {
        FinancePlusBackup backup = getBackup(user, id);
        try {
            Files.deleteIfExists(resolveFile(backup));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete backup", exception);
        }
        backupRepository.delete(backup);
    }

    @Transactional
    public void restore(User user, Long id, String passphrase) {
        validatePassphrase(passphrase);
        FinancePlusBackup backup = getBackup(user, id);
        try {
            byte[] archive = decrypt(Files.readAllBytes(resolveFile(backup)), passphrase);
            Map<String, Object> payload = objectMapper.readValue(archive, new TypeReference<>() {});
            if (!String.valueOf(user.getId()).equals(String.valueOf(payload.get("userId")))) {
                throw new IllegalArgumentException("Backup belongs to a different user");
            }
            validatePayload(payload);
            createBackup(user, passphrase, "PRE_RESTORE");
            restorePayload(user, payload);
            backup.setStatus("RESTORED");
            backupRepository.save(backup);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to restore backup", exception);
        }
    }

    private FinancePlusBackup createBackupWithKey(User user, SecretKey key, String backupType) {
        try {
            Map<String, Object> payload = createPayload(user);
            byte[] archive = objectMapper.writeValueAsBytes(payload);
            byte[] encrypted = encrypt(archive, key, settingsService.getOrCreateVaultSalt(user));
            LocalDateTime createdAt = LocalDateTime.now();
            String fileName = "finplus_user_" + user.getId() + "_" + FILE_TIME.format(createdAt) + ".fpbackup";
            Path path = backupDirectory.resolve(fileName);
            Files.write(path, encrypted, java.nio.file.StandardOpenOption.CREATE_NEW);
            FinancePlusBackup backup = new FinancePlusBackup();
            backup.setUser(user);
            backup.setFileName(fileName);
            backup.setFilePath(path.toString());
            backup.setFileSize(Files.size(path));
            backup.setBackupType(backupType.toUpperCase(Locale.ROOT));
            backup.setStatus("AVAILABLE");
            backup.setCreatedAt(createdAt);
            return backupRepository.save(backup);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create encrypted backup", exception);
        }
    }

    private Map<String, Object> createPayload(User user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 2);
        payload.put("userId", user.getId());
        payload.put("createdAt", LocalDateTime.now().toString());
        payload.put("accounts", accountRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::accountMap).toList());
        payload.put("income", incomeRepository.findByUserOrderByIncomeDateDesc(user).stream().map(this::incomeMap).toList());
        payload.put("expenses", expenseRepository.findByUserOrderByExpenseDateDesc(user).stream().map(this::expenseMap).toList());
        payload.put("budgets", budgetRepository.findByUser(user).stream().map(this::budgetMap).toList());
        payload.put("goals", goalRepository.findByUserOrderByTargetDateAsc(user).stream().map(this::goalMap).toList());
        payload.put("imports", importedTransactionRepository.findByUserOrderByTransactionDateDesc(user).stream().map(this::importMap).toList());
        payload.put("recurring", recurringRepository.findByUserOrderByNextDueDateAsc(user).stream().map(this::recurringMap).toList());
        payload.put("attachments", attachmentRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::attachmentMap).toList());
        return payload;
    }

    private void validatePayload(Map<String, Object> payload) {
        if (!Integer.valueOf(2).equals(toInteger(payload.get("version")))) {
            throw new IllegalArgumentException("Unsupported backup version");
        }
        for (String section : List.of("accounts", "income", "expenses", "budgets", "goals", "imports", "recurring", "attachments")) {
            if (!(payload.get(section) instanceof List<?>)) {
                throw new IllegalArgumentException("Backup is missing section: " + section);
            }
        }
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private void restorePayload(User user, Map<String, Object> payload) {
        attachmentRepository.deleteByUser(user);
        recurringRepository.deleteAll(recurringRepository.findByUserOrderByNextDueDateAsc(user));
        importedTransactionRepository.deleteAll(importedTransactionRepository.findByUserOrderByTransactionDateDesc(user));
        expenseRepository.deleteAll(expenseRepository.findByUserOrderByExpenseDateDesc(user));
        incomeRepository.deleteAll(incomeRepository.findByUserOrderByIncomeDateDesc(user));
        budgetRepository.deleteAll(budgetRepository.findByUser(user));
        goalRepository.deleteAll(goalRepository.findByUserOrderByTargetDateAsc(user));
        accountRepository.deleteAll(accountRepository.findByUserOrderByCreatedAtDesc(user));
        entityManager.flush();
        entityManager.clear();

        Map<String, Map<Long, Long>> idMaps = new java.util.HashMap<>();
        maps(payload, "accounts").forEach(values -> {
            Long oldId = longValue(values, "id");
            Account account = new Account();
            account.setUser(user);
            account.setAccountName(stringValue(values, "accountName"));
            account.setAccountType(stringValue(values, "accountType"));
            account.setBalance(decimalValue(values, "balance"));
            account.setInstitution(stringValue(values, "institution"));
            account.setNotes(stringValue(values, "notes"));
            account.setIsActive(booleanValue(values, "isActive"));
            rememberId(idMaps, "ACCOUNT", oldId, accountRepository.save(account).getId());
        });
        maps(payload, "income").forEach(values -> {
            Long oldId = longValue(values, "id");
            Income income = new Income();
            income.setUser(user);
            income.setDescription(stringValue(values, "description"));
            income.setAmount(decimalValue(values, "amount"));
            income.setIncomeDate(dateValue(values, "date"));
            income.setCategory(stringValue(values, "category"));
            income.setIncomeSource(stringValue(values, "source"));
            income.setNotes(stringValue(values, "notes"));
            income.setRecurring(booleanValue(values, "recurring"));
            income.setRecurrenceFrequency(stringValue(values, "frequency"));
            income.setNextOccurrence(nullableDateValue(values, "nextDueDate"));
            rememberId(idMaps, "INCOME", oldId, incomeRepository.save(income).getId());
        });
        maps(payload, "expenses").forEach(values -> {
            Long oldId = longValue(values, "id");
            Expense expense = new Expense();
            expense.setUser(user);
            expense.setDescription(stringValue(values, "description"));
            expense.setAmount(decimalValue(values, "amount"));
            expense.setExpenseDate(dateValue(values, "date"));
            expense.setCategory(stringValue(values, "category"));
            expense.setPaymentMethod(stringValue(values, "paymentMethod"));
            expense.setNotes(stringValue(values, "notes"));
            expense.setRecurring(booleanValue(values, "recurring"));
            expense.setRecurrenceFrequency(stringValue(values, "frequency"));
            expense.setNextOccurrence(nullableDateValue(values, "nextDueDate"));
            String receipt = stringValue(values, "receipt");
            if (!receipt.isBlank()) {
                expense.setReceiptImage(Base64.getDecoder().decode(receipt));
            }
            rememberId(idMaps, "EXPENSE", oldId, expenseRepository.save(expense).getId());
        });
        maps(payload, "budgets").forEach(values -> {
            Budget budget = new Budget();
            budget.setUser(user);
            budget.setCategory(stringValue(values, "category"));
            budget.setLimitAmount(decimalValue(values, "limit"));
            budget.setSpentAmount(decimalValue(values, "spent"));
            budget.setBudgetMonth(stringValue(values, "month"));
            budget.setIsActive(booleanValue(values, "active"));
            budget.setNotes(stringValue(values, "notes"));
            budgetRepository.save(budget);
        });
        maps(payload, "goals").forEach(values -> {
            FinancialGoal goal = new FinancialGoal();
            goal.setUser(user);
            goal.setGoalName(stringValue(values, "name"));
            goal.setDescription(stringValue(values, "description"));
            goal.setTargetAmount(decimalValue(values, "target"));
            goal.setCurrentAmount(decimalValue(values, "current"));
            goal.setTargetDate(dateValue(values, "date"));
            goal.setGoalStatus(stringValue(values, "status"));
            goal.setPriority(stringValue(values, "priority"));
            goalRepository.save(goal);
        });
        maps(payload, "imports").forEach(values -> {
            Long oldId = longValue(values, "id");
            ImportedTransaction transaction = new ImportedTransaction();
            transaction.setUser(user);
            transaction.setOriginalFilename(stringValue(values, "file"));
            transaction.setFileType(stringValue(values, "fileType"));
            transaction.setTransactionDate(dateValue(values, "date"));
            transaction.setDescription(stringValue(values, "description"));
            transaction.setTransactionType(stringValue(values, "type"));
            transaction.setAmount(decimalValue(values, "amount"));
            transaction.setCategory(stringValue(values, "category"));
            transaction.setBalance(decimalValue(values, "balance"));
            transaction.setExtractedText(stringValue(values, "extractedText"));
            transaction.setImportStatus(stringValue(values, "status"));
            transaction.setSourceImagePath(stringValue(values, "sourceImagePath"));
            rememberId(idMaps, "IMPORT", oldId, importedTransactionRepository.save(transaction).getId());
        });
        maps(payload, "recurring").forEach(values -> {
            Long oldId = longValue(values, "id");
            FinancePlusRecurring rule = new FinancePlusRecurring();
            rule.setUser(user);
            rule.setTransactionType(stringValue(values, "type"));
            rule.setDescription(stringValue(values, "description"));
            rule.setAmount(decimalValue(values, "amount"));
            rule.setCategory(stringValue(values, "category"));
            rule.setPaymentMethod(stringValue(values, "paymentMethod"));
            rule.setFrequency(stringValue(values, "frequency"));
            rule.setNextDueDate(dateValue(values, "nextDueDate"));
            rule.setActive(booleanValue(values, "active"));
            rule.setAutoPost(booleanValue(values, "autoPost"));
            rememberId(idMaps, "RECURRING", oldId, recurringRepository.save(rule).getId());
        });
        maps(payload, "attachments").forEach(values -> {
            String targetType = stringValue(values, "transactionType");
            Long oldTargetId = longValue(values, "transactionId");
            Long targetId = resolveId(idMaps, targetType, oldTargetId);
            if (targetId == null) {
                throw new IllegalArgumentException("Attachment target is missing from backup");
            }
            FinancePlusAttachment attachment = new FinancePlusAttachment();
            attachment.setUser(user);
            attachment.setTransactionType(targetType);
            attachment.setTransactionId(targetId);
            attachment.setOriginalFilename(stringValue(values, "filename"));
            attachment.setContentType(stringValue(values, "contentType"));
            attachment.setContentLength(Long.valueOf(stringValue(values, "contentLength")));
            attachment.setData(Base64.getDecoder().decode(stringValue(values, "data")));
            attachment.setSha256(stringValue(values, "sha256"));
            attachmentRepository.save(attachment);
        });
    }

    private void rememberId(Map<String, Map<Long, Long>> idMaps, String type, Long oldId, Long newId) {
        if (oldId != null) {
            idMaps.computeIfAbsent(type, ignored -> new java.util.HashMap<>()).put(oldId, newId);
        }
    }

    private Long resolveId(Map<String, Map<Long, Long>> idMaps, String type, Long oldId) {
        Map<Long, Long> ids = idMaps.get(type);
        return ids == null ? null : ids.get(oldId);
    }

    private Map<String, Object> accountMap(Account value) {
        return map("id", value.getId(), "accountName", value.getAccountName(), "accountType", value.getAccountType(),
                "balance", value.getBalance(), "institution", value.getInstitution(), "notes", value.getNotes(),
                "isActive", value.getIsActive());
    }

    private Map<String, Object> incomeMap(Income value) {
        return map("id", value.getId(), "description", value.getDescription(), "amount", value.getAmount(),
                "date", value.getIncomeDate(), "category", value.getCategory(), "source", value.getIncomeSource(),
                "notes", value.getNotes(), "recurring", value.isRecurring(), "frequency", value.getRecurrenceFrequency(),
                "nextDueDate", value.getNextOccurrence());
    }

    private Map<String, Object> expenseMap(Expense value) {
        return map("id", value.getId(), "description", value.getDescription(), "amount", value.getAmount(),
                "date", value.getExpenseDate(), "category", value.getCategory(), "paymentMethod", value.getPaymentMethod(),
                "notes", value.getNotes(), "recurring", value.isRecurring(), "frequency", value.getRecurrenceFrequency(),
                "nextDueDate", value.getNextOccurrence(), "receipt",
                value.getReceiptImage() == null ? "" : Base64.getEncoder().encodeToString(value.getReceiptImage()));
    }

    private Map<String, Object> budgetMap(Budget value) {
        return map("id", value.getId(), "category", value.getCategory(), "limit", value.getLimitAmount(),
                "spent", value.getSpentAmount(), "month", value.getBudgetMonth(), "active", value.getIsActive(),
                "notes", value.getNotes());
    }

    private Map<String, Object> goalMap(FinancialGoal value) {
        return map("id", value.getId(), "name", value.getGoalName(), "description", value.getDescription(),
                "target", value.getTargetAmount(), "current", value.getCurrentAmount(), "date", value.getTargetDate(),
                "status", value.getGoalStatus(), "priority", value.getPriority());
    }

    private Map<String, Object> importMap(ImportedTransaction value) {
        return map("id", value.getId(), "file", value.getOriginalFilename(), "fileType", value.getFileType(),
                "date", value.getTransactionDate(), "description", value.getDescription(), "type", value.getTransactionType(),
                "amount", value.getAmount(), "category", value.getCategory(), "balance", value.getBalance(),
                "extractedText", value.getExtractedText(), "status", value.getImportStatus(),
                "sourceImagePath", value.getSourceImagePath());
    }

    private Map<String, Object> recurringMap(FinancePlusRecurring value) {
        return map("id", value.getId(), "type", value.getTransactionType(), "description", value.getDescription(),
                "amount", value.getAmount(), "category", value.getCategory(), "paymentMethod", value.getPaymentMethod(),
                "frequency", value.getFrequency(), "nextDueDate", value.getNextDueDate(), "active", value.getActive(),
                "autoPost", value.getAutoPost());
    }

    private Map<String, Object> attachmentMap(com.finance.financeplus.model.FinancePlusAttachment value) {
        return map("id", value.getId(), "transactionType", value.getTransactionType(),
                "transactionId", value.getTransactionId(), "filename", value.getOriginalFilename(),
                "contentType", value.getContentType(), "contentLength", value.getContentLength(),
                "data", Base64.getEncoder().encodeToString(value.getData()), "sha256", value.getSha256());
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String stringValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private Long longValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : Long.valueOf(value.toString());
    }

    private java.math.BigDecimal decimalValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(value.toString());
    }

    private LocalDate dateValue(Map<String, Object> values, String key) {
        return LocalDate.parse(stringValue(values, key));
    }

    private LocalDate nullableDateValue(Map<String, Object> values, String key) {
        String value = stringValue(values, key);
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private boolean booleanValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private FinancePlusBackup getBackup(User user, Long id) {
        return backupRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found"));
    }

    private Path resolveFile(FinancePlusBackup backup) {
        if (backup.getFileName() == null || !backup.getFileName().matches(FILE_PATTERN)) {
            throw new IllegalArgumentException("Invalid backup filename");
        }
        Path path = backupDirectory.resolve(backup.getFileName()).normalize();
        if (!path.startsWith(backupDirectory) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Backup file is unavailable");
        }
        return path;
    }

    private byte[] encrypt(byte[] plaintext, SecretKey key, String saltValue) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltValue);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteArrayOutputStream output = new ByteArrayOutputStream(MAGIC.length + salt.length + iv.length + ciphertext.length);
            output.writeBytes(MAGIC);
            output.writeBytes(salt);
            output.writeBytes(iv);
            output.writeBytes(ciphertext);
            return output.toByteArray();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to encrypt backup", exception);
        }
    }

    private byte[] decrypt(byte[] encrypted, String passphrase) {
        try {
            int minimum = MAGIC.length + SALT_LENGTH + IV_LENGTH;
            if (encrypted.length <= minimum) {
                throw new IllegalArgumentException("Backup is incomplete");
            }
            int offset = 0;
            for (byte expected : MAGIC) {
                if (encrypted[offset++] != expected) {
                    throw new IllegalArgumentException("Backup format is invalid");
                }
            }
            byte[] storedSalt = new byte[SALT_LENGTH];
            System.arraycopy(encrypted, offset, storedSalt, 0, storedSalt.length);
            offset += storedSalt.length;
            String storedSaltValue = Base64.getEncoder().encodeToString(storedSalt);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(encrypted, offset, iv, 0, IV_LENGTH);
            offset += IV_LENGTH;
            byte[] ciphertext = new byte[encrypted.length - offset];
            System.arraycopy(encrypted, offset, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, storedSaltValue), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Incorrect passphrase or damaged backup", exception);
        }
    }

    private SecretKey deriveKey(String passphrase, String saltValue) {
        validatePassphrase(passphrase);
        try {
            byte[] salt = Base64.getDecoder().decode(saltValue);
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to derive backup key", exception);
        }
    }

    private void validatePassphrase(String passphrase) {
        if (passphrase == null || passphrase.length() < 8 || passphrase.length() > 128) {
            throw new IllegalArgumentException("Backup passphrase must contain 8 to 128 characters");
        }
    }

    private record CachedKey(SecretKey key, long expiresAt) {}
}
