package com.finance.service;

import com.finance.model.entity.Account;
import com.finance.model.entity.Budget;
import com.finance.model.entity.Expense;
import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
public class UserDataExportService {

    private final AccountService accountService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final BudgetService budgetService;
    private final FinancialGoalService financialGoalService;
    private final ImportFileService importFileService;

    public UserDataExportService(AccountService accountService, ExpenseService expenseService,
                                 IncomeService incomeService, BudgetService budgetService,
                                 FinancialGoalService financialGoalService, ImportFileService importFileService) {
        this.accountService = accountService;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
        this.budgetService = budgetService;
        this.financialGoalService = financialGoalService;
        this.importFileService = importFileService;
    }

    public byte[] exportExcel(User user) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            addProfile(workbook, headerStyle, user);
            addAccounts(workbook, headerStyle, accountService.getUserAccounts(user));
            addIncome(workbook, headerStyle, incomeService.getUserIncome(user));
            addExpenses(workbook, headerStyle, expenseService.getUserExpenses(user));
            addBudgets(workbook, headerStyle, budgetService.getUserAllBudgets(user));
            addGoals(workbook, headerStyle, financialGoalService.getUserGoals(user));
            addImports(workbook, headerStyle, importFileService.getImportedTransactions(user));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create Excel export", exception);
        }
    }

    private void addProfile(Workbook workbook, CellStyle headerStyle, User user) {
        Sheet sheet = workbook.createSheet("Profile");
        sheet.createRow(0).createCell(0).setCellValue("Field");
        sheet.getRow(0).createCell(1).setCellValue("Value");
        sheet.getRow(0).getCell(0).setCellStyle(headerStyle);
        sheet.getRow(0).getCell(1).setCellStyle(headerStyle);
        addRow(sheet, "Username", user.getUsername());
        addRow(sheet, "Email", user.getEmail());
        addRow(sheet, "Full name", user.getFullName());
        addRow(sheet, "Created at", format(user.getCreatedAt()));
        autoSize(sheet);
    }

    private void addAccounts(Workbook workbook, CellStyle headerStyle, java.util.List<Account> accounts) {
        Sheet sheet = workbook.createSheet("Accounts");
        header(sheet, headerStyle, "ID", "Name", "Type", "Balance", "Institution");
        for (Account account : accounts) {
            addRow(sheet, account.getId(), account.getAccountName(), account.getAccountType(),
                    account.getBalance(), account.getInstitution());
        }
        autoSize(sheet);
    }

    private void addIncome(Workbook workbook, CellStyle headerStyle, java.util.List<Income> incomes) {
        Sheet sheet = workbook.createSheet("Income");
        header(sheet, headerStyle, "ID", "Date", "Description", "Amount", "Category", "Source",
                "Notes", "Recurring", "Frequency", "Next occurrence");
        for (Income income : incomes) {
            addRow(sheet, income.getId(), income.getIncomeDate(), income.getDescription(), income.getAmount(),
                    income.getCategory(), income.getIncomeSource(), income.getNotes(), income.isRecurring(),
                    income.getRecurrenceFrequency(), income.getNextOccurrence());
        }
        autoSize(sheet);
    }

    private void addExpenses(Workbook workbook, CellStyle headerStyle, java.util.List<Expense> expenses) {
        Sheet sheet = workbook.createSheet("Expenses");
        header(sheet, headerStyle, "ID", "Date", "Description", "Amount", "Category", "Payment method",
                "Notes", "Recurring", "Frequency", "Next occurrence", "Receipt attached");
        for (Expense expense : expenses) {
            addRow(sheet, expense.getId(), expense.getExpenseDate(), expense.getDescription(), expense.getAmount(),
                    expense.getCategory(), expense.getPaymentMethod(), expense.getNotes(), expense.isRecurring(),
                    expense.getRecurrenceFrequency(), expense.getNextOccurrence(), expense.getReceiptImage() != null);
        }
        autoSize(sheet);
    }

    private void addBudgets(Workbook workbook, CellStyle headerStyle, java.util.List<Budget> budgets) {
        Sheet sheet = workbook.createSheet("Budgets");
        header(sheet, headerStyle, "ID", "Category", "Month", "Limit", "Spent", "Active", "Notes");
        for (Budget budget : budgets) {
            addRow(sheet, budget.getId(), budget.getCategory(), budget.getBudgetMonth(), budget.getLimitAmount(),
                    budget.getSpentAmount(), budget.getIsActive(), budget.getNotes());
        }
        autoSize(sheet);
    }

    private void addGoals(Workbook workbook, CellStyle headerStyle, java.util.List<FinancialGoal> goals) {
        Sheet sheet = workbook.createSheet("Goals");
        header(sheet, headerStyle, "ID", "Name", "Description", "Target", "Current", "Target date",
                "Status", "Priority");
        for (FinancialGoal goal : goals) {
            addRow(sheet, goal.getId(), goal.getGoalName(), goal.getDescription(), goal.getTargetAmount(),
                    goal.getCurrentAmount(), goal.getTargetDate(), goal.getGoalStatus(), goal.getPriority());
        }
        autoSize(sheet);
    }

    private void addImports(Workbook workbook, CellStyle headerStyle, java.util.List<ImportedTransaction> imports) {
        Sheet sheet = workbook.createSheet("Imported Transactions");
        header(sheet, headerStyle, "ID", "File", "Type", "Date", "Description", "Amount", "Category",
                "Transaction type", "Status");
        for (ImportedTransaction transaction : imports) {
            addRow(sheet, transaction.getId(), transaction.getOriginalFilename(), transaction.getFileType(),
                    transaction.getTransactionDate(), transaction.getDescription(), transaction.getAmount(),
                    transaction.getCategory(), transaction.getTransactionType(), transaction.getImportStatus());
        }
        autoSize(sheet);
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void header(Sheet sheet, CellStyle style, String... values) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
            row.getCell(index).setCellStyle(style);
        }
    }

    private void addRow(Sheet sheet, Object... values) {
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

    private void autoSize(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) {
            for (var cell : row) {
                sheet.setColumnWidth(cell.getColumnIndex(), Math.min(255,
                        Math.max(12, formatter.formatCellValue(cell).length() + 2)) * 256);
            }
        }
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
