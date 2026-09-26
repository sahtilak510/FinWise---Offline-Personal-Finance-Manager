package com.finance.service;

import com.finance.model.dto.ExtractedTransactionDTO;
import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.AccountRepository;
import com.finance.repository.BudgetRepository;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.FinancialGoalRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import com.finance.service.excel.CategoryExcelService;
import com.finance.service.excel.ExcelWorkbookInitializer;
import com.finance.service.excel.LocalExcelStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeatureRegressionTest {

    @Test
    void importValidationCleansAndAcceptsValidRecord() {
        ImportedTransactionRepository repository = mock(ImportedTransactionRepository.class);
        DocumentUploadService uploadService = mock(DocumentUploadService.class);
        DuplicateDetectionService duplicateService = mock(DuplicateDetectionService.class);
        CategoryEngine categoryEngine = mock(CategoryEngine.class);
        ImportFileService service = new ImportFileService(repository, uploadService, duplicateService, categoryEngine);
        User user = user(1L, "import-user");
        ImportedTransaction record = imported(user, "  Grocery   store  ", new BigDecimal("12.345"),
                LocalDate.of(2026, 9, 20), "EXPENSE", "");
        when(repository.findByIdAndUser(10L, user)).thenReturn(Optional.of(record));
        when(repository.findByUserOrderByTransactionDateDesc(user)).thenReturn(List.of());
        when(duplicateService.isDuplicate(eq(record), any())).thenReturn(false);
        when(categoryEngine.categorize(record)).thenReturn("Food");

        service.validateRecords(List.of(10L), user);

        assertEquals("Grocery store", record.getDescription());
        assertEquals(new BigDecimal("12.35"), record.getAmount());
        assertEquals("Food", record.getCategory());
        assertEquals("VALIDATED", record.getImportStatus());
        verify(repository).save(record);
    }

    @Test
    void importValidationRejectsInvalidRecordWithoutAcceptingIt() {
        ImportedTransactionRepository repository = mock(ImportedTransactionRepository.class);
        DuplicateDetectionService duplicateService = mock(DuplicateDetectionService.class);
        CategoryEngine categoryEngine = mock(CategoryEngine.class);
        ImportFileService service = new ImportFileService(repository, mock(DocumentUploadService.class),
                duplicateService, categoryEngine);
        User user = user(1L, "invalid-import-user");
        ImportedTransaction record = imported(user, "", BigDecimal.ZERO, LocalDate.now(), "OTHER", "Food");
        when(repository.findByIdAndUser(11L, user)).thenReturn(Optional.of(record));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.validateRecords(List.of(11L), user));

        assertTrue(exception.getMessage().contains("Description"));
        assertEquals("REJECTED", record.getImportStatus());
    }

    @Test
    void newImportsAreComparedWithPreviouslySavedImports() throws Exception {
        ImportedTransactionRepository repository = mock(ImportedTransactionRepository.class);
        DocumentUploadService uploadService = mock(DocumentUploadService.class);
        CategoryEngine categoryEngine = mock(CategoryEngine.class);
        ImportFileService service = new ImportFileService(repository, uploadService,
                new LocalDuplicateDetectionService(), categoryEngine);
        User user = user(2L, "duplicate-import-user");
        ImportedTransaction existing = imported(user, "Amazon purchase", new BigDecimal("49.99"),
                LocalDate.of(2026, 9, 10), "EXPENSE", "Shopping");
        ImportedTransaction incoming = imported(user, "Amazon purchase", new BigDecimal("49.99"),
                LocalDate.of(2026, 9, 10), "EXPENSE", "Shopping");
        when(repository.findByUserOrderByTransactionDateDesc(user)).thenReturn(List.of(existing));
        when(uploadService.processFile(any(MultipartFile.class), eq(user))).thenReturn(List.of(incoming));
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv",
                "date,description,type,amount,category".getBytes(StandardCharsets.UTF_8));

        List<ImportedTransaction> result = service.processFile(file, user);

        assertEquals(1, result.size());
        assertEquals("DUPLICATE", result.get(0).getImportStatus());
        verify(repository).saveAll(result);
    }

    @Test
    void blankPdfUsesIndependentBytesForOcrFallback() throws Exception {
        byte[] pdf = blankPdf();
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf);
        Tess4JOcrService ocrService = mock(Tess4JOcrService.class);
        when(ocrService.extractText(any(byte[].class))).thenReturn("Invoice 25.00 2026-09-20");
        LocalDocumentScanService service = new LocalDocumentScanService(mock(OpenCVPreprocessor.class),
                ocrService, new ScanTransactionParser(new LocalCategoryEngine()),
                mock(DuplicateDetectionService.class), new ExpenseService(mock(ExpenseRepository.class)),
                new IncomeService(mock(IncomeRepository.class)), mock(ExpenseRepository.class),
                mock(IncomeRepository.class));

        List<ExtractedTransactionDTO> result = service.scanRows(file, user(3L, "scan-user"));

        assertEquals(1, result.size());
        verify(ocrService).extractText(any(byte[].class));
    }

    @Test
    void categoryDeletionRejectsAnotherUsersCategory() throws Exception {
        Path directory = Files.createTempDirectory("finwise-category-test-");
        try {
            TestInitializer initializer = new TestInitializer(directory.resolve("Finwise_Data.xlsx"));
            initializer.initializeWorkbook();
            CategoryExcelService categoryService = new CategoryExcelService(new LocalExcelStorageService(initializer));
            User owner = user(4L, "category-owner");
            User attacker = user(5L, "category-attacker");
            var category = categoryService.createCategory(owner, "Travel", "EXPENSE", "", "", "#000000");
            String categoryId = category.getCategoryId();

            assertThrows(IllegalArgumentException.class, () -> categoryService.deleteCategory(attacker, categoryId));
            assertTrue(categoryService.getCategoryById(categoryId).isPresent());
            categoryService.deleteCategory(owner, categoryId);
            assertTrue(categoryService.getCategoryById(categoryId).isEmpty());
        } finally {
            FileSystemUtils.deleteRecursively(directory.toFile());
        }
    }

    @Test
    void recurringExpenseMaterializesDueOccurrences() {
        ExpenseRepository repository = mock(ExpenseRepository.class);
        ExpenseService service = new ExpenseService(repository);
        User user = user(6L, "recurring-user");
        Expense template = new Expense();
        template.setUser(user);
        template.setDescription("Rent");
        template.setAmount(new BigDecimal("500"));
        template.setExpenseDate(LocalDate.of(2026, 8, 1));
        template.setCategory("Rent");
        template.setPaymentMethod("Bank");
        template.setRecurring(true);
        template.setRecurrenceFrequency("MONTHLY");
        template.setNextOccurrence(LocalDate.of(2026, 9, 1));
        when(repository.findByUserOrderByExpenseDateDesc(user)).thenReturn(List.of(template));
        when(repository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int created = service.processDueRecurringTransactions(user, LocalDate.of(2026, 10, 1));

        assertEquals(2, created);
        assertEquals(LocalDate.of(2026, 11, 1), template.getNextOccurrence());
        verify(repository, times(3)).save(any(Expense.class));
    }

    @Test
    void cashFlowForecastUsesRecentMonthlyHistory() {
        IncomeRepository incomeRepository = mock(IncomeRepository.class);
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        IncomeService incomeService = new IncomeService(incomeRepository);
        ExpenseService expenseService = new ExpenseService(expenseRepository);
        CashFlowForecastService service = new CashFlowForecastService(incomeService, expenseService);
        User user = user(7L, "forecast-user");
        YearMonth target = YearMonth.of(2026, 10);
        List<Income> incomes = List.of(income(user, new BigDecimal("1000"), LocalDate.of(2026, 6, 5)),
                income(user, new BigDecimal("1100"), LocalDate.of(2026, 7, 5)),
                income(user, new BigDecimal("1200"), LocalDate.of(2026, 8, 5)));
        List<Expense> expenses = List.of(expense(user, new BigDecimal("400"), LocalDate.of(2026, 6, 8)),
                expense(user, new BigDecimal("450"), LocalDate.of(2026, 7, 8)),
                expense(user, new BigDecimal("500"), LocalDate.of(2026, 8, 8)));
        when(incomeRepository.findByUserOrderByIncomeDateDesc(user)).thenReturn(incomes);
        when(expenseRepository.findByUserOrderByExpenseDateDesc(user)).thenReturn(expenses);

        CashFlowForecastService.CashFlowForecast forecast = service.forecast(user, target);

        assertEquals(new BigDecimal("1100.00"), forecast.predictedIncome());
        assertEquals(new BigDecimal("450.00"), forecast.predictedExpense());
        assertEquals(new BigDecimal("650.00"), forecast.predictedNet());
        assertEquals("Positive", forecast.trend());
    }

    @Test
    void encryptedBackupRestoresDatabaseAndRejectsTraversal() throws Exception {
        Path directory = Files.createTempDirectory("finwise-backup-test-");
        try {
            Path databasePath = directory.resolve("finance.db");
            Path workbookPath = directory.resolve("Finwise_Data.xlsx");
            DataSource dataSource = dataSource(databasePath);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE backup_test (value TEXT)");
                statement.execute("INSERT INTO backup_test(value) VALUES ('before')");
            }
            TestInitializer initializer = new TestInitializer(workbookPath);
            initializer.initializeWorkbook();
            LocalExcelStorageService storageService = new LocalExcelStorageService(initializer);
            var backupService = new com.finance.service.excel.BackupRestoreService(storageService, initializer,
                    dataSource, "jdbc:sqlite:" + databasePath, "regression-test-key", 30);
            var backup = backupService.createBackup("1");
            byte[] encrypted = Files.readAllBytes(Path.of(backup.getFilePath()));
            assertEquals("FWB1", new String(encrypted, 0, 4, StandardCharsets.US_ASCII));
            assertNotEquals('P', (char) encrypted[0]);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("UPDATE backup_test SET value='after'");
            }

            backupService.restoreBackup(backup.getFileName(), "1");

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT value FROM backup_test")) {
                assertTrue(result.next());
                assertEquals("before", result.getString(1));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> backupService.restoreBackup("../finance.db", "1"));
        } finally {
            FileSystemUtils.deleteRecursively(directory.toFile());
        }
    }

    @Test
    void excelExportContainsExpectedUserSheets() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        IncomeRepository incomeRepository = mock(IncomeRepository.class);
        BudgetRepository budgetRepository = mock(BudgetRepository.class);
        FinancialGoalRepository goalRepository = mock(FinancialGoalRepository.class);
        ImportedTransactionRepository importRepository = mock(ImportedTransactionRepository.class);
        AccountService accountService = new AccountService(accountRepository);
        ExpenseService expenseService = new ExpenseService(expenseRepository);
        IncomeService incomeService = new IncomeService(incomeRepository);
        BudgetService budgetService = new BudgetService(budgetRepository);
        FinancialGoalService goalService = new FinancialGoalService(goalRepository);
        ImportFileService importService = new ImportFileService(importRepository,
                mock(DocumentUploadService.class), mock(DuplicateDetectionService.class), mock(CategoryEngine.class));
        UserDataExportService service = new UserDataExportService(accountService, expenseService, incomeService,
                budgetService, goalService, importService);
        User user = user(8L, "export-user");
        when(accountRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(expenseRepository.findByUserOrderByExpenseDateDesc(user)).thenReturn(List.of());
        when(incomeRepository.findByUserOrderByIncomeDateDesc(user)).thenReturn(List.of());
        when(budgetRepository.findByUser(user)).thenReturn(List.of());
        when(goalRepository.findByUserOrderByTargetDateAsc(user)).thenReturn(List.of());
        when(importRepository.findByUserOrderByTransactionDateDesc(user)).thenReturn(List.of());

        byte[] bytes = service.exportExcel(user);

        assertTrue(bytes.length > 0);
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Profile"));
            assertNotNull(workbook.getSheet("Expenses"));
            assertNotNull(workbook.getSheet("Imported Transactions"));
        } catch (Exception exception) {
            fail(exception);
        }
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static ImportedTransaction imported(User user, String description, BigDecimal amount,
                                               LocalDate date, String type, String category) {
        ImportedTransaction record = new ImportedTransaction();
        record.setUser(user);
        record.setDescription(description);
        record.setAmount(amount);
        record.setTransactionDate(date);
        record.setTransactionType(type);
        record.setCategory(category);
        record.setOriginalFilename("test.csv");
        record.setFileType("CSV");
        return record;
    }

    private static Income income(User user, BigDecimal amount, LocalDate date) {
        Income income = new Income();
        income.setUser(user);
        income.setAmount(amount);
        income.setIncomeDate(date);
        income.setDescription("Income");
        income.setCategory("Salary");
        income.setIncomeSource("Employer");
        return income;
    }

    private static Expense expense(User user, BigDecimal amount, LocalDate date) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setAmount(amount);
        expense.setExpenseDate(date);
        expense.setDescription("Expense");
        expense.setCategory("Food");
        expense.setPaymentMethod("Card");
        return expense;
    }

    private static byte[] blankPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static DataSource dataSource(Path databasePath) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + databasePath);
        return dataSource;
    }

    private static class TestInitializer extends ExcelWorkbookInitializer {
        private TestInitializer(Path workbookPath) {
            super(workbookPath);
        }
    }
}
