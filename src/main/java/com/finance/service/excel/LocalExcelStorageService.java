package com.finance.service.excel;

import org.apache.poi.ss.usermodel.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

@Service
public class LocalExcelStorageService {

    private final Path workbookPath;
    private final Path backupDir;
    private final ReentrantReadWriteLock workbookLock = new ReentrantReadWriteLock();
    private volatile Workbook cachedWorkbook = null;
    private volatile long lastModified = 0;

    public LocalExcelStorageService(ExcelWorkbookInitializer workbookInitializer) {
        this.workbookPath = workbookInitializer.getWorkbookPath();
        this.backupDir = workbookPath.getParent().resolve("backups");
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create backup directory: " + e.getMessage(), e);
        }
    }

    public <T> List<T> readSheet(String sheetName, RowMapper<T> mapper) {
        workbookLock.writeLock().lock();
        try {
            Workbook workbook = getWorkbook();
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                return Collections.emptyList();
            }
            List<T> results = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (isRowEmpty(row)) continue;
                try {
                    T mapped = mapper.mapRow(row);
                    if (mapped != null) {
                        results.add(mapped);
                    }
                } catch (Exception e) {
                    System.err.println("Error mapping row " + row.getRowNum() + " in sheet " + sheetName + ": " + e.getMessage());
                }
            }
            return results;
        } finally {
            workbookLock.writeLock().unlock();
        }
    }

    public <T> Optional<T> findById(String sheetName, String idColumnName, String idValue, RowMapper<T> mapper) {
        workbookLock.writeLock().lock();
        try {
            Workbook workbook = getWorkbook();
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) return Optional.empty();

            int idColIndex = findColumnIndex(sheet, idColumnName);
            if (idColIndex == -1) return Optional.empty();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (isRowEmpty(row)) continue;
                Cell cell = row.getCell(idColIndex);
                if (cell != null && getCellString(cell).equals(idValue)) {
                    try {
                        return Optional.ofNullable(mapper.mapRow(row));
                    } catch (Exception e) {
                        System.err.println("Error mapping row " + row.getRowNum() + ": " + e.getMessage());
                    }
                }
            }
            return Optional.empty();
        } finally {
            workbookLock.writeLock().unlock();
        }
    }

    public void writeSheet(String sheetName, Consumer<Sheet> modifier) {
        workbookLock.writeLock().lock();
        Path tempFile = null;

        try {
            Files.createDirectories(workbookPath.getParent());
            Workbook workbook = loadWorkbookForWrite();
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                workbook.close();
                throw new IllegalStateException("Sheet not found: " + sheetName);
            }

            modifier.accept(sheet);

            tempFile = File.createTempFile("finwise_", ".xlsx", workbookPath.getParent().toFile()).toPath();
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                workbook.write(fos);
            }
            workbook.close();
            cachedWorkbook = null;

            validateWorkbook(tempFile, sheetName);

            createBackup();

            try {
                Files.move(tempFile, workbookPath, StandardCopyOption.REPLACE_EXISTING);
                tempFile = null;
            } catch (java.nio.file.AccessDeniedException ade) {
                Thread.sleep(150);
                Files.copy(tempFile, workbookPath, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(tempFile);
                tempFile = null;
            }
            lastModified = Files.getLastModifiedTime(workbookPath).toMillis();

        } catch (Exception e) {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
            throw new RuntimeException("Failed to write to sheet " + sheetName + ": " + e.getMessage() + ". Your previous data has been preserved.", e);
        } finally {
            workbookLock.writeLock().unlock();
        }
    }

    public void appendRow(String sheetName, RowData rowData) {
        writeSheet(sheetName, sheet -> {
            int lastRowNum = sheet.getLastRowNum();
            Row newRow = sheet.createRow(lastRowNum + 1);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalStateException("Sheet has no header row: " + sheetName);
            }
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell headerCell = headerRow.getCell(i);
                if (headerCell == null) continue;
                String columnName = headerCell.getStringCellValue();
                Cell cell = newRow.createCell(i);
                Object value = rowData.get(columnName);
                setCellValue(cell, value);
            }
        });
    }

    public void updateRow(String sheetName, String idColumnName, String idValue, RowData rowData) {
        writeSheet(sheetName, sheet -> {
            int idColIndex = findColumnIndex(sheet, idColumnName);
            if (idColIndex == -1) {
                throw new IllegalStateException("ID column not found: " + idColumnName);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalStateException("Sheet has no header row: " + sheetName);
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (isRowEmpty(row)) continue;
                Cell idCell = row.getCell(idColIndex);
                if (idCell != null && getCellString(idCell).equals(idValue)) {
                    for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                        Cell headerCell = headerRow.getCell(i);
                        if (headerCell == null) continue;
                        String columnName = headerCell.getStringCellValue();
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        Object value = rowData.get(columnName);
                        setCellValue(cell, value);
                    }
                    return;
                }
            }
            throw new IllegalStateException("Record not found with " + idColumnName + "=" + idValue);
        });
    }

    public void deleteRow(String sheetName, String idColumnName, String idValue) {
        writeSheet(sheetName, sheet -> {
            int idColIndex = findColumnIndex(sheet, idColumnName);
            if (idColIndex == -1) {
                throw new IllegalStateException("ID column not found: " + idColumnName);
            }

            int rowToDelete = -1;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (isRowEmpty(row)) continue;
                Cell idCell = row.getCell(idColIndex);
                if (idCell != null && getCellString(idCell).equals(idValue)) {
                    rowToDelete = row.getRowNum();
                    break;
                }
            }

            if (rowToDelete == -1) {
                throw new IllegalStateException("Record not found with " + idColumnName + "=" + idValue);
            }

            if (rowToDelete == sheet.getLastRowNum()) {
                sheet.removeRow(sheet.getRow(rowToDelete));
            } else {
                sheet.shiftRows(rowToDelete + 1, sheet.getLastRowNum(), -1);
            }
        });
    }

    public void deleteRowsByUser(String userId, List<String> sheetNames) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (sheetNames == null || sheetNames.isEmpty()) {
            throw new IllegalArgumentException("At least one sheet is required");
        }

        purgeUserRowsFromBackups(userId, sheetNames);
        writeSheet(sheetNames.get(0), anchorSheet -> {
            Workbook workbook = anchorSheet.getWorkbook();
            for (String sheetName : sheetNames) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    throw new IllegalStateException("Sheet not found: " + sheetName);
                }
                removeRowsByUser(sheet, userId);
            }
        });
        purgeUserRowsFromBackups(userId, sheetNames);
    }

    private void purgeUserRowsFromBackups(String userId, List<String> sheetNames) {
        if (!Files.isDirectory(backupDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "Finwise_Backup_*.xlsx")) {
            for (Path backup : stream) {
                purgeUserRowsFromWorkbook(backup, userId, sheetNames);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to purge user data from workbook backups: " + e.getMessage(), e);
        }
    }

    private void purgeUserRowsFromWorkbook(Path path, String userId, List<String> sheetNames) throws IOException {
        Path tempFile = null;
        boolean changed = false;
        try {
            try (InputStream input = Files.newInputStream(path);
                 Workbook workbook = WorkbookFactory.create(input)) {
                for (String sheetName : sheetNames) {
                    Sheet sheet = workbook.getSheet(sheetName);
                    if (sheet != null) {
                        changed |= removeRowsByUser(sheet, userId);
                    }
                }
                if (changed) {
                    tempFile = Files.createTempFile(backupDir, "finwise_backup_", ".xlsx");
                    try (FileOutputStream output = new FileOutputStream(tempFile.toFile())) {
                        workbook.write(output);
                    }
                }
            }
            if (changed && tempFile != null) {
                try {
                    Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                    tempFile = null;
                } catch (java.nio.file.AccessDeniedException e) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while replacing workbook backup", interrupted);
                    }
                    Files.copy(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                    tempFile = null;
                }
            }
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private boolean removeRowsByUser(Sheet sheet, String userId) {
        int userIdColumn = findColumnIndex(sheet, "UserID");
        if (userIdColumn == -1) {
            throw new IllegalStateException("UserID column not found in sheet: " + sheet.getSheetName());
        }
        List<Integer> rowsToDelete = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isRowEmpty(row)) {
                continue;
            }
            Cell userIdCell = row.getCell(userIdColumn);
            if (userIdCell != null && getCellString(userIdCell).equals(userId)) {
                rowsToDelete.add(row.getRowNum());
            }
        }
        for (int i = rowsToDelete.size() - 1; i >= 0; i--) {
            Row row = sheet.getRow(rowsToDelete.get(i));
            if (row != null) {
                sheet.removeRow(row);
            }
        }
        return !rowsToDelete.isEmpty();
    }

    public String generateId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public void auditLog(String userId, String action, String entity, String recordId, String details) {
        RowData auditData = new RowData();
        auditData.put("AuditID", generateId("AUD"));
        auditData.put("UserID", userId);
        auditData.put("Action", action);
        auditData.put("Entity", entity);
        auditData.put("RecordID", recordId != null ? recordId : "");
        auditData.put("Timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("Details", details != null ? details : "");
        auditData.put("IPAddress", "localhost");
        appendRow("Audit", auditData);
    }

    private Workbook getWorkbook() {
        long currentModified = 0;
        try {
            currentModified = Files.getLastModifiedTime(workbookPath).toMillis();
        } catch (IOException ignored) {}

        if (cachedWorkbook != null && currentModified == lastModified) {
            return cachedWorkbook;
        }

        if (cachedWorkbook != null) {
            try { cachedWorkbook.close(); } catch (IOException ignored) {}
            cachedWorkbook = null;
        }
        try {
            cachedWorkbook = loadWorkbookSnapshot();
            lastModified = currentModified;
            return cachedWorkbook;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workbook: " + e.getMessage(), e);
        }
    }

    private Workbook loadWorkbookForWrite() throws IOException {
        if (cachedWorkbook != null) {
            try { cachedWorkbook.close(); } catch (IOException ignored) {}
            cachedWorkbook = null;
        }
        return loadWorkbookSnapshot();
    }

    private Workbook loadWorkbookSnapshot() throws IOException {
        try (InputStream input = Files.newInputStream(workbookPath)) {
            return WorkbookFactory.create(input);
        }
    }

    private void validateWorkbook(Path path, String modifiedSheetName) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(input)) {
            Sheet sheet = wb.getSheet(modifiedSheetName);
            if (sheet == null) {
                throw new IOException("Modified sheet missing after write: " + modifiedSheetName);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Header row missing in sheet: " + modifiedSheetName);
            }
            for (String requiredSheet : ExcelWorkbookInitializer.REQUIRED_SHEETS) {
                if (wb.getSheet(requiredSheet) == null) {
                    throw new IOException("Required sheet missing after write: " + requiredSheet);
                }
            }
        }
    }

    private void createBackup() {
        if (workbookPath == null || !Files.exists(workbookPath)) {
            return;
        }
        if (workbookPath.toFile().length() == 0) {
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path backupFile = backupDir.resolve("Finwise_Backup_" + timestamp + ".xlsx");
        for (int i = 0; i < 3; i++) {
            try {
                Files.copy(workbookPath, backupFile, StandardCopyOption.REPLACE_EXISTING);
                try {
                    cleanOldBackups(50);
                } catch (IOException cleanupEx) {
                    System.err.println("Warning: failed to clean old backups: " + cleanupEx.getMessage());
                }
                return;
            } catch (java.nio.file.FileSystemException fse) {
                // Windows file lock (Excel open, OneDrive sync, or concurrent instance).
                // Backup is best-effort only - never fail startup/writes because of it.
                try { Thread.sleep(100); } catch (InterruptedException ignored2) { Thread.currentThread().interrupt(); }
                if (i == 2) {
                    System.err.println("Warning: skipping Excel backup (file locked): " + fse.getMessage());
                    return;
                }
            } catch (IOException ioe) {
                System.err.println("Warning: skipping Excel backup: " + ioe.getMessage());
                return;
            }
        }
    }

    private void cleanOldBackups(int maxBackups) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "Finwise_Backup_*.xlsx")) {
            List<Path> backups = new ArrayList<>();
            for (Path p : stream) backups.add(p);
            backups.sort((left, right) -> right.toString().compareTo(left.toString()));
            for (int i = maxBackups; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }

    private int findColumnIndex(Sheet sheet, String columnName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return -1;
        for (Cell cell : headerRow) {
            if (columnName.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getStringCellValue().trim();
            default -> "";
        };
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof String s) {
            cell.setCellValue(s);
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (value instanceof LocalDateTime ldt) {
            cell.setCellValue(ldt);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    public interface RowMapper<T> {
        T mapRow(Row row);
    }

    public static class RowData extends HashMap<String, Object> {
        public RowData put(@NonNull String key, Object value) {
            super.put(key, value);
            return this;
        }
    }
}