package com.finance.service.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ExcelWorkbookInitializer {

    private static final String DEFAULT_WORKBOOK_PATH = "data/Finwise_Data.xlsx";
    private final Path workbookPath;

    public static final List<String> REQUIRED_SHEETS = List.of(
            "Users", "Profiles", "Income", "Expenses", "Budgets", "Goals",
            "Categories", "Settings", "Imports", "OCR_Data", "Duplicate_Checks",
            "Analytics", "Forecasts", "Reports", "AI_Chat", "Audit", "Notifications"
    );

    @Autowired
    public ExcelWorkbookInitializer(@Value("${app.workbook-path:data/Finwise_Data.xlsx}") String workbookPath) {
        this(Path.of(workbookPath));
    }

    public ExcelWorkbookInitializer() {
        this(Path.of(DEFAULT_WORKBOOK_PATH));
    }

    protected ExcelWorkbookInitializer(Path workbookPath) {
        this.workbookPath = workbookPath.toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initializeWorkbook() {
        try {
            Files.createDirectories(workbookPath.getParent());
            if (!Files.exists(workbookPath)) {
                createWorkbookWithSheets();
            } else {
                validateAndRepairWorkbook();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Finwise_Data.xlsx: " + e.getMessage(), e);
        }
    }

    private void createWorkbookWithSheets() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(workbookPath.toFile())) {

            createUsersSheet(workbook);
            createProfilesSheet(workbook);
            createIncomeSheet(workbook);
            createExpensesSheet(workbook);
            createBudgetsSheet(workbook);
            createGoalsSheet(workbook);
            createCategoriesSheet(workbook);
            createSettingsSheet(workbook);
            createImportsSheet(workbook);
            createOCRDataSheet(workbook);
            createDuplicateChecksSheet(workbook);
            createAnalyticsSheet(workbook);
            createForecastsSheet(workbook);
            createReportsSheet(workbook);
            createAIChatSheet(workbook);
            createAuditSheet(workbook);
            createNotificationsSheet(workbook);

            workbook.write(fos);
        }
    }

    private void validateAndRepairWorkbook() throws IOException {
        try (Workbook workbook = WorkbookFactory.create(workbookPath.toFile())) {
            boolean modified = false;
            for (String sheetName : REQUIRED_SHEETS) {
                if (workbook.getSheet(sheetName) == null) {
                    createSheet(workbook, sheetName);
                    modified = true;
                } else {
                    validateHeaders(workbook.getSheet(sheetName), sheetName);
                }
            }
            if (modified) {
                try (FileOutputStream fos = new FileOutputStream(workbookPath.toFile())) {
                    workbook.write(fos);
                }
            }
        }
    }

    private void createSheet(Workbook workbook, String sheetName) {
        switch (sheetName) {
            case "Users" -> createUsersSheet(workbook);
            case "Profiles" -> createProfilesSheet(workbook);
            case "Income" -> createIncomeSheet(workbook);
            case "Expenses" -> createExpensesSheet(workbook);
            case "Budgets" -> createBudgetsSheet(workbook);
            case "Goals" -> createGoalsSheet(workbook);
            case "Categories" -> createCategoriesSheet(workbook);
            case "Settings" -> createSettingsSheet(workbook);
            case "Imports" -> createImportsSheet(workbook);
            case "OCR_Data" -> createOCRDataSheet(workbook);
            case "Duplicate_Checks" -> createDuplicateChecksSheet(workbook);
            case "Analytics" -> createAnalyticsSheet(workbook);
            case "Forecasts" -> createForecastsSheet(workbook);
            case "Reports" -> createReportsSheet(workbook);
            case "AI_Chat" -> createAIChatSheet(workbook);
            case "Audit" -> createAuditSheet(workbook);
            case "Notifications" -> createNotificationsSheet(workbook);
        }
    }

    private void validateHeaders(Sheet sheet, String sheetName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            createHeaderRow(sheet, requiredHeaders(sheetName));
        }
    }

    private String[] requiredHeaders(String sheetName) {
        return switch (sheetName) {
            case "Users" -> new String[]{"UserID", "Username", "Email", "PasswordHash", "FullName", "IsActive",
                    "ProfileImage", "Theme", "Language", "NotificationsEnabled", "BudgetAlertsEnabled",
                    "DailySummaryEnabled", "GoalUpdatesEnabled", "CreatedAt", "UpdatedAt"};
            case "Profiles" -> new String[]{"ProfileID", "UserID", "Currency", "DateFormat", "NumberFormat", "Timezone",
                    "DefaultAccount", "AvatarPath", "CreatedAt", "UpdatedAt"};
            case "Income" -> new String[]{"IncomeID", "UserID", "Description", "Amount", "IncomeDate", "Category",
                    "IncomeSource", "Notes", "IsRecurring", "Frequency", "NextOccurrence", "LastOccurrence",
                    "Reference", "AttachmentPath", "CreatedAt", "UpdatedAt"};
            case "Expenses" -> new String[]{"ExpenseID", "UserID", "Description", "Amount", "ExpenseDate", "ExpenseTime",
                    "Category", "Subcategory", "Merchant", "PaymentMethod", "Account", "Notes",
                    "IsRecurring", "Frequency", "NextOccurrence", "LastOccurrence", "Reference",
                    "AttachmentPath", "ReceiptImage", "CreatedAt", "UpdatedAt"};
            case "Budgets" -> new String[]{"BudgetID", "UserID", "Category", "LimitAmount", "SpentAmount", "BudgetMonth",
                    "StartDate", "EndDate", "AlertThreshold50", "AlertThreshold75", "AlertThreshold90",
                    "AlertThreshold100", "IsActive", "Notes", "CreatedAt", "UpdatedAt"};
            case "Goals" -> new String[]{"GoalID", "UserID", "GoalName", "Description", "GoalType", "TargetAmount",
                    "CurrentAmount", "TargetDate", "Priority", "Status", "IsActive", "CreatedAt", "UpdatedAt"};
            case "Categories" -> new String[]{"CategoryID", "UserID", "Name", "Type", "ParentCategoryID", "Icon", "Color",
                    "IsDefault", "SortOrder", "CreatedAt", "UpdatedAt"};
            case "Settings" -> new String[]{"SettingID", "UserID", "SettingKey", "SettingValue", "SettingType",
                    "Description", "CreatedAt", "UpdatedAt"};
            case "Imports" -> new String[]{"ImportID", "UserID", "OriginalFilename", "FileType", "FileSize",
                    "ImportDate", "ProcessingStatus", "OCRStatus", "TransactionCount", "DuplicateCount",
                    "ValidatedCount", "RejectedCount", "CreatedAt", "UpdatedAt"};
            case "OCR_Data" -> new String[]{"OCRID", "UserID", "ImportID", "OriginalFilename", "ExtractedText",
                    "Merchant", "Date", "Amount", "Tax", "Description", "PaymentMethod",
                    "InvoiceReference", "Confidence", "ProcessingStatus", "CreatedAt", "UpdatedAt"};
            case "Duplicate_Checks" -> new String[]{"CheckID", "UserID", "ImportID", "ExistingRecordID", "MatchType",
                    "MatchScore", "Date", "Amount", "Merchant", "Description", "TransactionType",
                    "Resolution", "CreatedAt"};
            case "Analytics" -> new String[]{"AnalyticsID", "UserID", "PeriodStart", "PeriodEnd", "TotalIncome",
                    "TotalExpenses", "NetBalance", "SavingsRate", "ExpenseIncomeRatio", "AvgDailySpending",
                    "AvgMonthlySpending", "HighestSpendingCategory", "HighestSpendingMerchant",
                    "IncomeGrowth", "ExpenseGrowth", "BudgetUtilization", "GoalProgress", "CreatedAt"};
            case "Forecasts" -> new String[]{"ForecastID", "UserID", "ForecastDate", "ForecastType", "PeriodStart",
                    "PeriodEnd", "PredictedAmount", "Confidence", "Method", "BasisPeriodMonths",
                    "Category", "Details", "CreatedAt"};
            case "Reports" -> new String[]{"ReportID", "UserID", "ReportType", "PeriodStart", "PeriodEnd",
                    "GeneratedAt", "Format", "FilePath", "Status", "Details", "CreatedAt"};
            case "AI_Chat" -> new String[]{"MessageID", "UserID", "Role", "Content", "Intent", "Confidence",
                    "ContextData", "Timestamp"};
            case "Audit" -> new String[]{"AuditID", "UserID", "Action", "Entity", "RecordID", "Timestamp",
                    "Details", "IPAddress"};
            case "Notifications" -> new String[]{"NotificationID", "UserID", "Type", "Priority", "Title", "Message",
                    "IsRead", "CreatedAt", "ReadAt"};
            default -> throw new IllegalArgumentException("Unknown required sheet: " + sheetName);
        };
    }

    private void createUsersSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Users");
        String[] headers = {"UserID", "Username", "Email", "PasswordHash", "FullName", "IsActive",
                "ProfileImage", "Theme", "Language", "NotificationsEnabled", "BudgetAlertsEnabled",
                "DailySummaryEnabled", "GoalUpdatesEnabled", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 20, 30, 60, 25, 8, 40, 10, 12, 15, 15, 15, 15, 22, 22});
    }

    private void createProfilesSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Profiles");
        String[] headers = {"ProfileID", "UserID", "Currency", "DateFormat", "NumberFormat", "Timezone",
                "DefaultAccount", "AvatarPath", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 10, 15, 15, 15, 15, 40, 22, 22});
    }

    private void createIncomeSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Income");
        String[] headers = {"IncomeID", "UserID", "Description", "Amount", "IncomeDate", "Category",
                "IncomeSource", "Notes", "IsRecurring", "Frequency", "NextOccurrence", "LastOccurrence",
                "Reference", "AttachmentPath", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 30, 15, 15, 15, 20, 40, 12, 12, 15, 15, 20, 40, 22, 22});
    }

    private void createExpensesSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Expenses");
        String[] headers = {"ExpenseID", "UserID", "Description", "Amount", "ExpenseDate", "ExpenseTime",
                "Category", "Subcategory", "Merchant", "PaymentMethod", "Account", "Notes",
                "IsRecurring", "Frequency", "NextOccurrence", "LastOccurrence", "Reference",
                "AttachmentPath", "ReceiptImage", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 30, 15, 15, 10, 15, 15, 20, 15, 15, 40, 12, 12, 15, 15, 20, 40, 15, 22, 22});
    }

    private void createBudgetsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Budgets");
        String[] headers = {"BudgetID", "UserID", "Category", "LimitAmount", "SpentAmount", "BudgetMonth",
                "StartDate", "EndDate", "AlertThreshold50", "AlertThreshold75", "AlertThreshold90",
                "AlertThreshold100", "IsActive", "Notes", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 15, 15, 15, 12, 15, 15, 12, 12, 12, 12, 10, 40, 22, 22});
    }

    private void createGoalsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Goals");
        String[] headers = {"GoalID", "UserID", "GoalName", "Description", "GoalType", "TargetAmount",
                "CurrentAmount", "TargetDate", "Priority", "Status", "IsActive", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 25, 40, 15, 15, 15, 15, 10, 12, 10, 22, 22});
    }

    private void createCategoriesSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Categories");
        String[] headers = {"CategoryID", "UserID", "Name", "Type", "ParentCategoryID", "Icon", "Color",
                "IsDefault", "SortOrder", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 20, 10, 15, 10, 10, 10, 10, 22, 22});
    }

    private void createSettingsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Settings");
        String[] headers = {"SettingID", "UserID", "SettingKey", "SettingValue", "SettingType",
                "Description", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 30, 50, 15, 40, 22, 22});
    }

    private void createImportsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Imports");
        String[] headers = {"ImportID", "UserID", "OriginalFilename", "FileType", "FileSize",
                "ImportDate", "ProcessingStatus", "OCRStatus", "TransactionCount", "DuplicateCount",
                "ValidatedCount", "RejectedCount", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 40, 12, 12, 22, 15, 12, 12, 12, 12, 12, 22, 22});
    }

    private void createOCRDataSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("OCR_Data");
        String[] headers = {"OCRID", "UserID", "ImportID", "OriginalFilename", "ExtractedText",
                "Merchant", "Date", "Amount", "Tax", "Description", "PaymentMethod",
                "InvoiceReference", "Confidence", "ProcessingStatus", "CreatedAt", "UpdatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 12, 40, 60, 30, 15, 15, 15, 40, 15, 25, 10, 15, 22, 22});
    }

    private void createDuplicateChecksSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Duplicate_Checks");
        String[] headers = {"CheckID", "UserID", "ImportID", "ExistingRecordID", "MatchType",
                "MatchScore", "Date", "Amount", "Merchant", "Description", "TransactionType",
                "Resolution", "CreatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 12, 12, 15, 10, 15, 15, 20, 30, 15, 15, 22});
    }

    private void createAnalyticsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Analytics");
        String[] headers = {"AnalyticsID", "UserID", "PeriodStart", "PeriodEnd", "TotalIncome",
                "TotalExpenses", "NetBalance", "SavingsRate", "ExpenseIncomeRatio", "AvgDailySpending",
                "AvgMonthlySpending", "HighestSpendingCategory", "HighestSpendingMerchant",
                "IncomeGrowth", "ExpenseGrowth", "BudgetUtilization", "GoalProgress", "CreatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 15, 15, 15, 15, 15, 12, 15, 15, 15, 20, 20, 12, 12, 15, 12, 22});
    }

    private void createForecastsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Forecasts");
        String[] headers = {"ForecastID", "UserID", "ForecastDate", "ForecastType", "PeriodStart",
                "PeriodEnd", "PredictedAmount", "Confidence", "Method", "BasisPeriodMonths",
                "Category", "Details", "CreatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 15, 15, 15, 15, 15, 10, 15, 12, 15, 50, 22});
    }

    private void createReportsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Reports");
        String[] headers = {"ReportID", "UserID", "ReportType", "PeriodStart", "PeriodEnd",
                "GeneratedAt", "Format", "FilePath", "Status", "Details", "CreatedAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 20, 15, 15, 22, 10, 40, 12, 50, 22});
    }

    private void createAIChatSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("AI_Chat");
        String[] headers = {"MessageID", "UserID", "Role", "Content", "Intent", "Confidence",
                "ContextData", "Timestamp"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 10, 60, 20, 10, 60, 22});
    }

    private void createAuditSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Audit");
        String[] headers = {"AuditID", "UserID", "Action", "Entity", "RecordID", "Timestamp",
                "Details", "IPAddress"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 20, 15, 12, 22, 60, 20});
    }

    private void createNotificationsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Notifications");
        String[] headers = {"NotificationID", "UserID", "Type", "Priority", "Title", "Message",
                "IsRead", "CreatedAt", "ReadAt"};
        createHeaderRow(sheet, headers);
        setColumnWidths(sheet, new int[]{12, 12, 20, 10, 30, 60, 10, 22, 22});
    }

    private void createHeaderRow(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
    }

    private void setColumnWidths(Sheet sheet, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    public Path getWorkbookPath() {
        return workbookPath;
    }

    public boolean workbookExists() {
        return Files.exists(workbookPath);
    }
}