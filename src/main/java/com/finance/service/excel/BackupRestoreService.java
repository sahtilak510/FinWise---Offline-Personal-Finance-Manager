package com.finance.service.excel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupRestoreService {

    private static final byte[] MAGIC = "FWB1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private static final String BACKUP_REGEX = "Finwise_Backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}\\.finwise";

    private final LocalExcelStorageService storageService;
    private final ExcelWorkbookInitializer workbookInitializer;
    private final DataSource dataSource;
    private final Path databasePath;
    private final Path backupDir;
    private final byte[] configuredEncryptionKey;
    private final int retentionDays;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public BackupRestoreService(LocalExcelStorageService storageService,
                                ExcelWorkbookInitializer workbookInitializer,
                                DataSource dataSource,
                                @Value("${spring.datasource.url:jdbc:sqlite:data/finance.db}") String databaseUrl,
                                @Value("${app.backup.encryption-key:}") String configuredEncryptionKey,
                                @Value("${app.backup.retention-days:30}") int retentionDays) {
        this.storageService = storageService;
        this.workbookInitializer = workbookInitializer;
        this.dataSource = dataSource;
        this.databasePath = resolveDatabasePath(databaseUrl);
        this.backupDir = workbookInitializer.getWorkbookPath().getParent().resolve("backups");
        this.configuredEncryptionKey = resolveConfiguredKey(configuredEncryptionKey);
        this.retentionDays = Math.max(1, retentionDays);
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create backup directory", e);
        }
    }

    public synchronized BackupInfo createBackup(String userId) {
        Path databaseSnapshot = null;
        try {
            databaseSnapshot = Files.createTempFile(backupDir, "database-", ".db");
            Files.deleteIfExists(databaseSnapshot);
            createDatabaseSnapshot(databaseSnapshot);

            byte[] archive = createArchive(databaseSnapshot);
            byte[] encrypted = encrypt(archive);
            LocalDateTime createdAt = LocalDateTime.now();
            String fileName = "Finwise_Backup_" + FILE_TIMESTAMP.format(createdAt) + ".finwise";
            Path backupFile = backupDir.resolve(fileName);
            Files.write(backupFile, encrypted);

            BackupInfo info = new BackupInfo(fileName, backupFile.toString(), encrypted.length,
                    createdAt, "AVAILABLE", userId == null ? "SYSTEM" : userId);
            trimOldBackups();
            return info;
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to create encrypted backup", e);
        } finally {
            if (databaseSnapshot != null) {
                try {
                    Files.deleteIfExists(databaseSnapshot);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public List<BackupInfo> listBackups() {
        List<BackupInfo> backups = new ArrayList<>();
        try (Stream<Path> stream = Files.list(backupDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(BACKUP_REGEX))
                    .forEach(path -> {
                        BackupInfo info = getBackupInfo(path.getFileName().toString());
                        if (info != null) {
                            backups.add(info);
                        }
                    });
        } catch (IOException ignored) {
        }
        backups.sort(Comparator.comparing(BackupInfo::getTimestamp).reversed());
        return backups;
    }

    public synchronized void restoreBackup(String fileName, String userId) {
        Path backupFile = resolveBackupFile(fileName);
        createBackup(userId);
        Path extractedDirectory = null;
        try {
            byte[] archive = decrypt(Files.readAllBytes(backupFile));
            extractedDirectory = Files.createTempDirectory(backupDir, "restore-");
            extractArchive(archive, extractedDirectory);

            Path restoredWorkbook = extractedDirectory.resolve("Finwise_Data.xlsx");
            Path restoredDatabase = extractedDirectory.resolve("finance.db");
            if (!Files.isRegularFile(restoredWorkbook) || !Files.isRegularFile(restoredDatabase)) {
                throw new IllegalArgumentException("Backup archive is incomplete");
            }

            restoreDatabase(restoredDatabase);
            Files.copy(restoredWorkbook, workbookInitializer.getWorkbookPath(), StandardCopyOption.REPLACE_EXISTING);
            storageService.auditLog(userId, "RESTORE", "System", "",
                    "Restored encrypted backup: " + fileName);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to restore encrypted backup", e);
        } finally {
            if (extractedDirectory != null) {
                try (Stream<Path> stream = Files.walk(extractedDirectory)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized void deleteBackup(String fileName) {
        try {
            Files.deleteIfExists(resolveBackupFile(fileName));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete backup", e);
        }
    }

    public BackupInfo getBackupInfo(String fileName) {
        try {
            Path backupFile = resolveBackupFile(fileName);
            if (!Files.isRegularFile(backupFile)) {
                return null;
            }
            String timestampValue = fileName.replace("Finwise_Backup_", "")
                    .replace(".finwise", "")
                    .replace('_', 'T')
                    .replaceFirst("T(\\d{2})-(\\d{2})-(\\d{2})-(\\d{3})$", "T$1:$2:$3.$4");
            LocalDateTime timestamp = LocalDateTime.parse(timestampValue);
            return new BackupInfo(fileName, backupFile.toString(), Files.size(backupFile), timestamp,
                    "AVAILABLE", "");
        } catch (Exception exception) {
            return null;
        }
    }

    public Path getBackupDirectory() {
        return backupDir;
    }

    private void createDatabaseSnapshot(Path snapshot) throws IOException, SQLException {
        Files.deleteIfExists(snapshot);
        String escapedPath = snapshot.toAbsolutePath().toString().replace("'", "''");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escapedPath + "'");
        } catch (SQLException exception) {
            if (!Files.isRegularFile(databasePath)) {
                throw exception;
            }
            Files.copy(databasePath, snapshot, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!Files.isRegularFile(snapshot) || Files.size(snapshot) == 0) {
            throw new IOException("Database snapshot is empty");
        }
    }

    private byte[] createArchive(Path databaseSnapshot) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            Path workbook = workbookInitializer.getWorkbookPath();
            zip.putNextEntry(new ZipEntry("Finwise_Data.xlsx"));
            Files.copy(workbook, zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("finance.db"));
            Files.copy(databaseSnapshot, zip);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private void extractArchive(byte[] archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path resolved = destination.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(destination)) {
                    throw new IOException("Invalid backup entry");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private void restoreDatabase(Path restoredDatabase) throws SQLException {
        String escapedPath = restoredDatabase.toAbsolutePath().toString().replace("'", "''");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("PRAGMA foreign_keys=OFF");
                statement.execute("ATTACH DATABASE '" + escapedPath + "' AS finwise_restore");
                List<String> tables = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT name FROM finwise_restore.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                    while (resultSet.next()) {
                        tables.add(resultSet.getString(1));
                    }
                }
                for (String table : tables) {
                    String quotedTable = quoteIdentifier(table);
                    statement.executeUpdate("DELETE FROM main." + quotedTable);
                    statement.executeUpdate("INSERT INTO main." + quotedTable
                            + " SELECT * FROM finwise_restore." + quotedTable);
                }
                connection.commit();
                statement.execute("DETACH DATABASE finwise_restore");
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                statement.execute("PRAGMA foreign_keys=ON");
            }
        }
    }

    private byte[] encrypt(byte[] plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText);
            ByteArrayOutputStream output = new ByteArrayOutputStream(MAGIC.length + iv.length + cipherText.length);
            output.write(MAGIC);
            output.write(iv);
            output.write(cipherText);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt backup", exception);
        }
    }

    private byte[] decrypt(byte[] encrypted) {
        try {
            if (encrypted.length <= MAGIC.length + IV_LENGTH) {
                throw new IllegalArgumentException("Backup file is invalid");
            }
            for (int index = 0; index < MAGIC.length; index++) {
                if (encrypted[index] != MAGIC[index]) {
                    throw new IllegalArgumentException("Backup file is invalid");
                }
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(encrypted, MAGIC.length, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[encrypted.length - MAGIC.length - IV_LENGTH];
            System.arraycopy(encrypted, MAGIC.length + IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Backup cannot be decrypted", exception);
        }
    }

    private SecretKeySpec encryptionKey() throws IOException {
        if (configuredEncryptionKey != null) {
            return new SecretKeySpec(configuredEncryptionKey, "AES");
        }
        Path keyFile = backupDir.resolve(".backup.key");
        if (Files.exists(keyFile)) {
            return new SecretKeySpec(Files.readAllBytes(keyFile), "AES");
        }
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);
        try {
            Files.write(keyFile, key, StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            key = Files.readAllBytes(keyFile);
        }
        if (key.length != 32) {
            throw new IllegalStateException("Backup encryption key is invalid");
        }
        return new SecretKeySpec(key, "AES");
    }

    private byte[] resolveConfiguredKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to configure backup encryption", exception);
        }
    }

    private Path resolveBackupFile(String fileName) {
        if (fileName == null || !fileName.matches(BACKUP_REGEX)) {
            throw new IllegalArgumentException("Invalid backup file name");
        }
        Path resolved = backupDir.resolve(fileName).normalize();
        if (!resolved.startsWith(backupDir)) {
            throw new IllegalArgumentException("Invalid backup file name");
        }
        return resolved;
    }

    private void trimOldBackups() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        for (BackupInfo backup : listBackups()) {
            if (backup.getTimestamp().isBefore(cutoff)) {
                try {
                    Files.deleteIfExists(backupDir.resolve(backup.getFileName()));
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Path resolveDatabasePath(String databaseUrl) {
        String value = databaseUrl == null || databaseUrl.isBlank()
                ? "jdbc:sqlite:data/finance.db"
                : databaseUrl;
        if (!value.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("Encrypted backup restore supports SQLite only");
        }
        String fileName = value.substring("jdbc:sqlite:".length());
        return Path.of(fileName).toAbsolutePath().normalize();
    }

    private String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    public static class BackupInfo {
        private final String fileName;
        private final String filePath;
        private final long fileSize;
        private final LocalDateTime timestamp;
        private final String status;
        private final String userId;

        public BackupInfo(String fileName, String filePath, long fileSize, LocalDateTime timestamp,
                          String status, String userId) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.timestamp = timestamp;
            this.status = status;
            this.userId = userId;
        }

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public String getFormattedSize() {
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", fileSize / 1024.0);
            return String.format(Locale.ROOT, "%.1f MB", fileSize / (1024.0 * 1024.0));
        }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public String getUserId() { return userId; }
    }
}
