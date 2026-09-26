package com.finance.service;

import com.finance.model.dto.ExtractedTransactionDTO;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import com.finance.repository.AccountRepository;
import com.finance.repository.BudgetRepository;
import com.finance.repository.ChatMessageRepository;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.FinancialGoalRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import com.finance.repository.UserRepository;
import com.finance.service.excel.ExcelWorkbookInitializer;
import com.finance.service.excel.LocalExcelStorageService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineFeatureTest {

    @Test
    void categoryEngineRecognizesFoodAndSalary() {
        LocalCategoryEngine engine = new LocalCategoryEngine();

        assertEquals("Food", engine.categorize("Lunch at local cafe", "Cafe", new BigDecimal("25.50"), "EXPENSE"));
        assertEquals("Salary", engine.categorize("Monthly salary deposit", "Employer", new BigDecimal("3200.00"), "INCOME"));
    }

    @Test
    void scanParserExtractsEveryTransactionRow() {
        ScanTransactionParser parser = new ScanTransactionParser(new LocalCategoryEngine());
        String text = "Finwise Import Test\n"
                + "Description Type Amount Date\n"
                + "Salary Credit INCOME $2500.00 2026-09-01\n"
                + "Grocery Store EXPENSE $125.40 2026-09-02\n"
                + "Electric Utility EXPENSE $84.10 2026-09-03";

        List<ExtractedTransactionDTO> rows = parser.parseRows(text, "IMAGE");

        assertEquals(3, rows.size());
        assertEquals("Salary Credit", rows.get(0).getMerchant());
        assertEquals(new BigDecimal("2500.00"), rows.get(0).getAmount());
        assertEquals("INCOME", rows.get(0).getTransactionType());
        assertEquals("Grocery Store", rows.get(1).getMerchant());
        assertEquals(new BigDecimal("125.40"), rows.get(1).getAmount());
        assertEquals("Electric Utility", rows.get(2).getMerchant());
        assertEquals(new BigDecimal("84.10"), rows.get(2).getAmount());
    }

    @Test
    void duplicateDetectionFlagsMatchingTransactions() {
        LocalDuplicateDetectionService service = new LocalDuplicateDetectionService();

        ImportedTransaction first = new ImportedTransaction();
        first.setDescription("Amazon purchase");
        first.setTransactionDate(LocalDate.of(2025, 2, 12));
        first.setAmount(new BigDecimal("49.99"));
        first.setTransactionType("EXPENSE");

        ImportedTransaction second = new ImportedTransaction();
        second.setDescription("Amazon purchase");
        second.setTransactionDate(LocalDate.of(2025, 2, 12));
        second.setAmount(new BigDecimal("49.99"));
        second.setTransactionType("EXPENSE");

        assertTrue(service.isDuplicate(first, second));
        assertFalse(service.detectDuplicates(List.of(first, second)).isEmpty());
    }

    @Test
    void duplicateDetectionRespectsUserBoundary() {
        LocalDuplicateDetectionService service = new LocalDuplicateDetectionService();

        User firstUser = new User();
        firstUser.setId(1L);
        User secondUser = new User();
        secondUser.setId(2L);

        ImportedTransaction first = new ImportedTransaction();
        first.setUser(firstUser);
        first.setDescription("Amazon purchase");
        first.setTransactionDate(LocalDate.of(2025, 2, 12));
        first.setAmount(new BigDecimal("49.99"));
        first.setTransactionType("EXPENSE");

        ImportedTransaction second = new ImportedTransaction();
        second.setUser(secondUser);
        second.setDescription("Amazon purchase");
        second.setTransactionDate(LocalDate.of(2025, 2, 12));
        second.setAmount(new BigDecimal("49.99"));
        second.setTransactionType("EXPENSE");

        assertFalse(service.isDuplicate(first, second));
        assertTrue(service.detectDuplicates(List.of(first, second)).isEmpty());
    }

    @Test
    void imageImportStoresOneSourceImageForAllParsedRecords() throws Exception {
        ImportedTransactionRepository importRepository = mock(ImportedTransactionRepository.class);
        DocumentUploadService documentUploadService = mock(DocumentUploadService.class);
        DuplicateDetectionService duplicateDetectionService = mock(DuplicateDetectionService.class);
        CategoryEngine categoryEngine = mock(CategoryEngine.class);
        ImportFileService service = new ImportFileService(
                importRepository,
                documentUploadService,
                duplicateDetectionService,
                categoryEngine
        );

        User user = new User();
        user.setId(42L);
        ImportedTransaction first = new ImportedTransaction();
        first.setUser(user);
        first.setCategory("Uncategorized");
        ImportedTransaction second = new ImportedTransaction();
        second.setUser(user);
        second.setCategory("Uncategorized");
        List<ImportedTransaction> parsedRecords = List.of(first, second);
        when(documentUploadService.processFile(any(MultipartFile.class), eq(user))).thenReturn(parsedRecords);
        when(duplicateDetectionService.detectDuplicates(anyList())).thenReturn(List.of());

        byte[] imageBytes = {1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", imageBytes);

        try {
            List<ImportedTransaction> result = service.processFile(file, user);

            assertEquals(2, result.size());
            assertNotNull(first.getSourceImagePath());
            assertEquals(first.getSourceImagePath(), second.getSourceImagePath());

            Path imagePath = Paths.get("data", "import-images").resolve(first.getSourceImagePath());
            assertTrue(Files.exists(imagePath));
            assertArrayEquals(imageBytes, Files.readAllBytes(imagePath));
            verify(importRepository).saveAll(result);
        } finally {
            if (first.getSourceImagePath() != null) {
                Files.deleteIfExists(Paths.get("data", "import-images").resolve(first.getSourceImagePath()));
            }
        }
    }

    @Test
    void concurrentWorkbookWritesAreSerializedWithoutDataLoss() throws Exception {
        Path tempDirectory = Files.createTempDirectory("finwise-storage-test-");
        Path workbookPath = tempDirectory.resolve("Finwise_Data.xlsx");
        ExcelWorkbookInitializer initializer = new TestExcelWorkbookInitializer(workbookPath);
        LocalExcelStorageService storageService = new LocalExcelStorageService(initializer);
        ExecutorService executor = Executors.newFixedThreadPool(12);

        try {
            createWorkbook(workbookPath);
            int writeCount = 12;
            CountDownLatch ready = new CountDownLatch(writeCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> writes = new ArrayList<>();

            for (int index = 0; index < writeCount; index++) {
                int current = index;
                writes.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    LocalExcelStorageService.RowData settings = new LocalExcelStorageService.RowData();
                    settings.put("SettingID", "SET-" + current);
                    settings.put("UserID", "concurrent-user");
                    settings.put("SettingKey", "key-" + current);
                    storageService.appendRow("Settings", settings);

                    LocalExcelStorageService.RowData category = new LocalExcelStorageService.RowData();
                    category.put("CategoryID", "CAT-" + current);
                    category.put("UserID", "concurrent-user");
                    category.put("Name", "Category " + current);
                    storageService.appendRow("Categories", category);
                    return null;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> write : writes) {
                write.get(30, TimeUnit.SECONDS);
            }

            assertEquals(writeCount, storageService.readSheet("Settings",
                    row -> row.getCell(0).getStringCellValue()).size());
            assertEquals(writeCount, storageService.readSheet("Categories",
                    row -> row.getCell(0).getStringCellValue()).size());
        } finally {
            executor.shutdownNow();
            FileSystemUtils.deleteRecursively(tempDirectory);
        }
    }

    @Test
    void resetPreferencesRestoresServerAndWorkbookDefaults() {
        UserRepository userRepository = mock(UserRepository.class);
        RecordingExcelStorageService storageService = new RecordingExcelStorageService();
        UserService service = new UserService(
                userRepository,
                mock(AccountRepository.class),
                mock(ExpenseRepository.class),
                mock(IncomeRepository.class),
                mock(BudgetRepository.class),
                mock(FinancialGoalRepository.class),
                mock(ImportedTransactionRepository.class),
                mock(ChatMessageRepository.class),
                mock(PasswordEncoder.class),
                storageService
        );
        User user = new User();
        user.setId(42L);
        user.setTheme("dark");
        user.setLanguage("hi");
        user.setNotificationsEnabled(false);
        user.setBudgetAlertsEnabled(false);
        user.setDailySummaryEnabled(false);
        user.setGoalUpdatesEnabled(false);
        when(userRepository.save(user)).thenReturn(user);

        User result = service.resetPreferences(user);

        assertEquals("light", result.getTheme());
        assertEquals("en", result.getLanguage());
        assertTrue(result.getNotificationsEnabled());
        assertTrue(result.getBudgetAlertsEnabled());
        assertTrue(result.getDailySummaryEnabled());
        assertTrue(result.getGoalUpdatesEnabled());
        assertEquals("42", storageService.deletedUserId);
        assertEquals(List.of("Settings"), storageService.deletedSheets);
    }

    @Test
    void deleteAccountRemovesWorkbookDataAndUser() {
        UserRepository userRepository = mock(UserRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        IncomeRepository incomeRepository = mock(IncomeRepository.class);
        BudgetRepository budgetRepository = mock(BudgetRepository.class);
        FinancialGoalRepository goalRepository = mock(FinancialGoalRepository.class);
        ImportedTransactionRepository importRepository = mock(ImportedTransactionRepository.class);
        ChatMessageRepository chatRepository = mock(ChatMessageRepository.class);
        RecordingExcelStorageService storageService = new RecordingExcelStorageService();
        UserService service = new UserService(
                userRepository,
                accountRepository,
                expenseRepository,
                incomeRepository,
                budgetRepository,
                goalRepository,
                importRepository,
                chatRepository,
                mock(PasswordEncoder.class),
                storageService
        );
        User user = new User();
        user.setId(42L);
        when(accountRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(expenseRepository.findByUserOrderByExpenseDateDesc(user)).thenReturn(List.of());
        when(incomeRepository.findByUserOrderByIncomeDateDesc(user)).thenReturn(List.of());
        when(budgetRepository.findByUser(user)).thenReturn(List.of());
        when(goalRepository.findByUserOrderByTargetDateAsc(user)).thenReturn(List.of());
        when(importRepository.findByUserOrderByTransactionDateDesc(user)).thenReturn(List.of());

        service.deleteAccountAndData(user);

        verify(chatRepository).deleteByUser(user);
        assertEquals("42", storageService.deletedUserId);
        assertEquals(List.of(
                "Users", "Profiles", "Income", "Expenses", "Budgets", "Goals",
                "Categories", "Settings", "Imports", "OCR_Data", "Duplicate_Checks",
                "Analytics", "Forecasts", "Reports", "AI_Chat", "Audit", "Notifications"
        ), storageService.deletedSheets);
        verify(userRepository).delete(user);
    }

    private static void createWorkbook(Path workbookPath) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (String sheetName : ExcelWorkbookInitializer.REQUIRED_SHEETS) {
                workbook.createSheet(sheetName);
            }
            writeHeader(workbook.getSheet("Settings"), "SettingID", "UserID", "SettingKey");
            writeHeader(workbook.getSheet("Categories"), "CategoryID", "UserID", "Name");
            try (var output = Files.newOutputStream(workbookPath)) {
                workbook.write(output);
            }
        }
    }

    private static void writeHeader(Sheet sheet, String... columns) {
        Row header = sheet.createRow(0);
        for (int index = 0; index < columns.length; index++) {
            header.createCell(index).setCellValue(columns[index]);
        }
    }

    private static class TestExcelWorkbookInitializer extends ExcelWorkbookInitializer {
        private final Path workbookPath;

        private TestExcelWorkbookInitializer(Path workbookPath) {
            this.workbookPath = workbookPath;
        }

        @Override
        public Path getWorkbookPath() {
            return workbookPath;
        }
    }

    private static class RecordingExcelStorageService extends LocalExcelStorageService {
        private String deletedUserId;
        private List<String> deletedSheets;

        private RecordingExcelStorageService() {
            super(new ExcelWorkbookInitializer());
        }

        @Override
        public void deleteRowsByUser(String userId, List<String> sheetNames) {
            deletedUserId = userId;
            deletedSheets = sheetNames;
        }
    }
}
