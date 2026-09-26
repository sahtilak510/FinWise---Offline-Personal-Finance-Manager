package com.finance.financeplus.service;

import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.service.ExpenseService;
import com.finance.service.IncomeService;
import com.finance.service.ImportFileService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class FinancePlusExportService {

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final ImportFileService importFileService;

    public FinancePlusExportService(ExpenseService expenseService, IncomeService incomeService,
                                    ImportFileService importFileService) {
        this.expenseService = expenseService;
        this.incomeService = incomeService;
        this.importFileService = importFileService;
    }

    public byte[] exportCsv(User user) {
        StringBuilder csv = new StringBuilder();
        csv.append("Type,Date,Description,Category,Amount,Status,Source\n");
        for (Income income : incomeService.getUserIncome(user)) {
            append(csv, "INCOME", income.getIncomeDate(), income.getDescription(), income.getCategory(),
                    income.getAmount(), "RECORDED", "Income");
        }
        for (Expense expense : expenseService.getUserExpenses(user)) {
            append(csv, "EXPENSE", expense.getExpenseDate(), expense.getDescription(), expense.getCategory(),
                    expense.getAmount(), "RECORDED", "Expense");
        }
        for (ImportedTransaction transaction : importFileService.getImportedTransactions(user)) {
            append(csv, transaction.getTransactionType(), transaction.getTransactionDate(), transaction.getDescription(),
                    transaction.getCategory(), transaction.getAmount(), transaction.getImportStatus(),
                    transaction.getOriginalFilename());
        }
        byte[] output = csv.toString().getBytes(StandardCharsets.UTF_8);
        if (output.length >= 3 && output[0] == (byte) 0xEF && output[1] == (byte) 0xBB && output[2] == (byte) 0xBF) {
            return output;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream(output.length + 3);
        result.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        result.writeBytes(output);
        return result.toByteArray();
    }

    private void append(StringBuilder csv, String type, Object date, String description, String category,
                         BigDecimal amount, String status, String source) {
        csv.append(csv(type)).append(',')
                .append(csv(date == null ? "" : date.toString())).append(',')
                .append(csv(description)).append(',')
                .append(csv(category)).append(',')
                .append(csv(amount)).append(',')
                .append(csv(status)).append(',')
                .append(csv(source)).append('\n');
    }

    private String csv(Object value) {
        String text = value == null ? "" : value.toString();
        String safeText = text;
        if (!safeText.isEmpty() && "=+-@".indexOf(safeText.charAt(0)) >= 0) {
            safeText = "'" + safeText;
        }
        return "\"" + safeText.replace("\"", "\"\"") + "\"";
    }
}
