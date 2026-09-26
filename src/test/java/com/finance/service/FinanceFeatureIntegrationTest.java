package com.finance.service;

import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.service.excel.CategoryExcelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.finance.app.OfflineFinanceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Transactional
class FinanceFeatureIntegrationTest {

    @Autowired
    UserService userService;

    @Autowired
    ExpenseService expenseService;

    @Autowired
    IncomeService incomeService;

    @Autowired
    BudgetService budgetService;

    @Autowired
    FinancialGoalService goalService;

    @Autowired
    AccountService accountService;

    @Autowired
    CategoryExcelService categoryExcelService;

    @Autowired
    ImportFileService importFileService;

    @Autowired
    ExpenseRepository expenseRepository;

    @Autowired
    ImportedTransactionRepository importRepository;

    @Autowired
    MockMvc mockMvc;

    @Test
    void coreFinanceCrudUsesAuthenticatedUserBoundaries() {
        User owner = register("crud-owner");
        User other = register("crud-other");
        Expense expense = expenseService.addExpense(owner, "Lunch", new BigDecimal("25.50"),
                LocalDate.of(2026, 9, 20), "Food", "Card", "Team lunch", new byte[]{1, 2, 3});
        expenseService.updateExpense(owner, expense.getId(), "Lunch updated", new BigDecimal("30"),
                LocalDate.of(2026, 9, 21), "Food", "Card", "Updated", true, "MONTHLY");
        assertTrue(expenseService.getExpenseById(expense.getId(), owner).orElseThrow().isRecurring());
        assertTrue(expenseService.getExpenseById(expense.getId(), other).isEmpty());

        Income income = incomeService.addIncome(owner, "Salary", new BigDecimal("3000"),
                LocalDate.of(2026, 9, 1), "Salary", "Employer", "", true, "MONTHLY");
        assertTrue(incomeService.getIncomeById(income.getId(), owner).orElseThrow().isRecurring());
        assertTrue(incomeService.getIncomeById(income.getId(), other).isEmpty());

        var budget = budgetService.createBudget(owner, "Food", new BigDecimal("500"),
                YearMonth.of(2026, 9).toString(), "Lunch budget");
        budgetService.updateBudget(owner, budget.getId(), new BigDecimal("600"),
                new BigDecimal("30"), "Updated budget");
        assertTrue(budgetService.getBudgetById(budget.getId(), other).isEmpty());

        var goal = goalService.createGoal(owner, "Emergency fund", "Rainy day", new BigDecimal("5000"),
                LocalDate.of(2027, 1, 1), "HIGH");
        goalService.updateGoalProgress(owner, goal.getId(), new BigDecimal("1000"));
        assertEquals("IN_PROGRESS", goalService.getGoalById(goal.getId(), owner).orElseThrow().getGoalStatus());
        assertTrue(goalService.getGoalById(goal.getId(), other).isEmpty());

        expenseService.deleteExpense(owner, expense.getId());
        incomeService.deleteIncome(owner, income.getId());
        budgetService.deleteBudget(owner, budget.getId());
        goalService.deleteGoal(owner, goal.getId());
        assertTrue(expenseService.getExpenseById(expense.getId(), owner).isEmpty());
        assertTrue(incomeService.getIncomeById(income.getId(), owner).isEmpty());
        assertTrue(budgetService.getBudgetById(budget.getId(), owner).isEmpty());
        assertTrue(goalService.getGoalById(goal.getId(), owner).isEmpty());
    }

    @Test
    void listAmountInputsValidateValuesAndRespectUserBoundaries() throws Exception {
        User owner = register("list-input-owner");
        User attacker = register("list-input-attacker");
        var budget = budgetService.createBudget(owner, "Food", new BigDecimal("500"),
                YearMonth.of(2026, 9).toString(), "Lunch budget");
        var goal = goalService.createGoal(owner, "Emergency fund", "Rainy day", new BigDecimal("1000"),
                LocalDate.of(2027, 1, 1), "HIGH");

        mockMvc.perform(get("/budgets").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"spentAmount\"")))
                .andExpect(content().string(containsString("min=\"0\"")))
                .andExpect(content().string(containsString("max=\"1000000000\"")));
        mockMvc.perform(get("/goals").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"currentAmount\"")))
                .andExpect(content().string(containsString("min=\"0\"")));

        mockMvc.perform(post("/budgets/spent/{id}", budget.getId())
                        .with(user(owner.getUsername())).with(csrf())
                        .param("spentAmount", "-1"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(BigDecimal.ZERO,
                budgetService.getBudgetById(budget.getId(), owner).orElseThrow().getSpentAmount());

        mockMvc.perform(post("/budgets/spent/{id}", budget.getId())
                        .with(user(owner.getUsername())).with(csrf())
                        .param("spentAmount", "123.456"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("successMessage"));
        assertEquals(new BigDecimal("123.46"),
                budgetService.getBudgetById(budget.getId(), owner).orElseThrow().getSpentAmount());

        mockMvc.perform(post("/budgets/spent/{id}", budget.getId())
                        .with(user(attacker.getUsername())).with(csrf())
                        .param("spentAmount", "500"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(new BigDecimal("123.46"),
                budgetService.getBudgetById(budget.getId(), owner).orElseThrow().getSpentAmount());

        mockMvc.perform(post("/goals/progress/{id}", goal.getId())
                        .with(user(owner.getUsername())).with(csrf())
                        .param("currentAmount", "-1"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(BigDecimal.ZERO,
                goalService.getGoalById(goal.getId(), owner).orElseThrow().getCurrentAmount());

        mockMvc.perform(post("/goals/progress/{id}", goal.getId())
                        .with(user(owner.getUsername())).with(csrf())
                        .param("currentAmount", "1000"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("successMessage"));
        assertEquals("COMPLETED",
                goalService.getGoalById(goal.getId(), owner).orElseThrow().getGoalStatus());
    }

    @Test
    void accountAndCategoryEditInputsValidateValuesAndOwnership() throws Exception {
        User owner = register("edit-list-owner");
        User attacker = register("edit-list-attacker");
        var account = accountService.createAccount(owner, "Primary Bank", "CHECKING",
                new BigDecimal("100"), "Local Bank");
        var category = categoryExcelService.createCategory(owner, "Groceries", "EXPENSE",
                "", "G", "#6366f1");

        try {
            mockMvc.perform(get("/accounts").with(user(owner.getUsername())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"accountName\"")))
                    .andExpect(content().string(containsString("name=\"balance\"")))
                    .andExpect(content().string(containsString("min=\"0\"")));
            mockMvc.perform(get("/categories").with(user(owner.getUsername())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"name\"")))
                    .andExpect(content().string(containsString("Check &amp; Save")));

            mockMvc.perform(post("/accounts/update/{id}", account.getId())
                            .with(user(owner.getUsername())).with(csrf())
                            .param("accountName", "Primary Bank")
                            .param("accountType", "SAVINGS")
                            .param("balance", "-1")
                            .param("institution", "Local Bank"))
                    .andExpect(status().isFound())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertEquals("CHECKING", accountService.getByIdAndUser(account.getId(), owner).orElseThrow().getAccountType());

            mockMvc.perform(post("/accounts/update/{id}", account.getId())
                            .with(user(attacker.getUsername())).with(csrf())
                            .param("accountName", "Stolen Account")
                            .param("accountType", "CASH")
                            .param("balance", "500")
                            .param("institution", ""))
                    .andExpect(status().isFound())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertEquals("Primary Bank",
                    accountService.getByIdAndUser(account.getId(), owner).orElseThrow().getAccountName());

            mockMvc.perform(post("/accounts/update/{id}", account.getId())
                            .with(user(owner.getUsername())).with(csrf())
                            .param("accountName", "Primary Savings")
                            .param("accountType", "SAVINGS")
                            .param("balance", "250.125")
                            .param("institution", "Local Bank"))
                    .andExpect(status().isFound())
                    .andExpect(flash().attributeExists("successMessage"));
            var updatedAccount = accountService.getByIdAndUser(account.getId(), owner).orElseThrow();
            assertEquals("Primary Savings", updatedAccount.getAccountName());
            assertEquals(new BigDecimal("250.13"), updatedAccount.getBalance());

            mockMvc.perform(post("/categories/update/{id}", category.getCategoryId())
                            .with(user(attacker.getUsername())).with(csrf())
                            .param("name", "Stolen Category")
                            .param("type", "EXPENSE")
                            .param("icon", "X")
                            .param("color", "#112233"))
                    .andExpect(status().isFound())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertEquals("Groceries",
                    categoryExcelService.getCategoryById(category.getCategoryId()).orElseThrow().getName());

            mockMvc.perform(post("/categories/update/{id}", category.getCategoryId())
                            .with(user(owner.getUsername())).with(csrf())
                            .param("name", " ")
                            .param("type", "EXPENSE")
                            .param("icon", "G")
                            .param("color", "#6366f1"))
                    .andExpect(status().isFound())
                    .andExpect(flash().attributeExists("errorMessage"));

            mockMvc.perform(post("/categories/update/{id}", category.getCategoryId())
                            .with(user(owner.getUsername())).with(csrf())
                            .param("name", "Food and Groceries")
                            .param("type", "INCOME")
                            .param("icon", "F")
                            .param("color", "#22c55e"))
                    .andExpect(status().isFound())
                    .andExpect(flash().attributeExists("successMessage"));
            var updatedCategory = categoryExcelService.getCategoryById(category.getCategoryId()).orElseThrow();
            assertEquals("Food and Groceries", updatedCategory.getName());
            assertEquals("INCOME", updatedCategory.getType());
        } finally {
            categoryExcelService.deleteCategory(owner, category.getCategoryId());
        }
    }

    @Test
    void csrfIsRequiredForStateChangingRequests() throws Exception {
        User user = register("csrf-user");

        mockMvc.perform(post("/settings/reset").with(user(user.getUsername())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/reset").with(user(user.getUsername())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void crossUserExpenseIdCannotBeOpenedOrDeleted() throws Exception {
        User owner = register("expense-owner");
        User attacker = register("expense-attacker");
        Expense expense = expenseService.addExpense(owner, "Private expense", new BigDecimal("15"),
                LocalDate.of(2026, 9, 20), "Other", "Cash", "", null);

        mockMvc.perform(get("/expenses/edit/{id}", expense.getId()).with(user(attacker.getUsername())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses/delete/{id}", expense.getId()).with(user(attacker.getUsername())))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/expenses/delete/{id}", expense.getId()).with(user(attacker.getUsername())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/expenses/delete/{id}", expense.getId())
                        .with(user(attacker.getUsername())).with(csrf()))
                .andExpect(status().isFound());
        assertTrue(expenseRepository.findById(expense.getId()).isPresent());
    }

    @Test
    void multipartReceiptCanBeUploadedAndDownloadedByOwnerOnly() throws Exception {
        User owner = register("receipt-owner");
        User attacker = register("receipt-attacker");
        MockMultipartFile receipt = new MockMultipartFile("receipt", "receipt.png", "image/png",
                new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/expenses/add")
                        .file(receipt)
                        .with(user(owner.getUsername()))
                        .with(csrf())
                        .param("description", "Receipt expense")
                        .param("amount", "42.00")
                        .param("expenseDate", "2026-09-20")
                        .param("category", "Food")
                        .param("paymentMethod", "Card")
                        .param("notes", "With receipt")
                        .param("recurring", "false"))
                .andExpect(status().isFound());

        Expense saved = expenseService.getUserExpenses(owner).stream()
                .filter(expense -> expense.getDescription().equals("Receipt expense"))
                .findFirst().orElseThrow();
        assertNotNull(saved.getReceiptImage());
        mockMvc.perform(get("/expenses/receipt/{id}", saved.getId()).with(user(attacker.getUsername())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses/receipt/{id}", saved.getId()).with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void importFixtureIsParsedAndPersisted() throws Exception {
        User user = register("fixture-import-user");
        ClassPathResource resource = new ClassPathResource("fixtures/import/finwise-import-sample.csv");
        MockMultipartFile file = new MockMultipartFile("file", resource.getFilename(),
                "text/csv", resource.getInputStream().readAllBytes());

        var records = importFileService.processFile(file, user);

        assertEquals(3, records.size());
        assertTrue(records.stream().allMatch(record -> record.getUser().getId().equals(user.getId())));
        assertTrue(records.stream().anyMatch(record -> "INCOME".equals(record.getTransactionType())));
        assertTrue(records.stream().allMatch(record -> record.getAmount().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void excelExportIsAvailableForAuthenticatedUser() throws Exception {
        User user = register("export-user");

        byte[] response = mockMvc.perform(get("/account/export-data")
                        .param("format", "xlsx").with(user(user.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals('P', response[0]);
        assertEquals('K', response[1]);
    }

    @Test
    void importingSelectedRecordsCreatesRealEntriesAndLeavesOtherRowsPending() throws Exception {
        User owner = register("import-commit-owner");
        ImportedTransaction expense = saveImported(owner, "Electric Utility", new BigDecimal("84.10"),
                LocalDate.of(2026, 9, 3), "EXPENSE", "Utilities");
        ImportedTransaction income = saveImported(owner, "Salary Credit", new BigDecimal("2500"),
                LocalDate.of(2026, 9, 1), "INCOME", "Salary");

        mockMvc.perform(post("/import/validate").with(user(owner.getUsername())).with(csrf())
                        .param("action", "import")
                        .param("validIds", expense.getId().toString())
                        .param("description_" + expense.getId(), "Electric Utility Bill")
                        .param("type_" + expense.getId(), "EXPENSE")
                        .param("category_" + expense.getId(), "Utilities"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("successMessage"));

        Expense saved = expenseService.getUserExpenses(owner).stream()
                .filter(item -> item.getDescription().equals("Electric Utility Bill"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("84.10"), saved.getAmount());
        assertEquals(LocalDate.of(2026, 9, 3), saved.getExpenseDate());
        assertEquals("Utilities", saved.getCategory());
        assertTrue(incomeService.getUserIncome(owner).isEmpty());

        assertEquals("IMPORTED",
                importRepository.findByIdAndUser(expense.getId(), owner).orElseThrow().getImportStatus());
        assertEquals("PARSED",
                importRepository.findByIdAndUser(income.getId(), owner).orElseThrow().getImportStatus());
        assertTrue(importFileService.getPendingImportedTransactions(owner).stream()
                .noneMatch(record -> record.getId().equals(expense.getId())));
    }

    @Test
    void duplicateImportIsHiddenAndOnlyDetailedMainRecordIsAdded() throws Exception {
        User owner = register("import-duplicate-owner");
        ImportedTransaction main = saveImported(owner, "Salary Credit", new BigDecimal("2500"),
                LocalDate.of(2026, 9, 1), "INCOME", "Salary");
        ImportedTransaction duplicate = saveImported(owner, "Salary Deposit", new BigDecimal("2500"),
                LocalDate.of(2026, 9, 1), "INCOME", "Salary");
        duplicate.setImportStatus("DUPLICATE");
        importRepository.save(duplicate);

        mockMvc.perform(get("/import/preview").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Salary Credit")))
                .andExpect(content().string(not(containsString("Salary Deposit"))));

        mockMvc.perform(post("/import/validate").with(user(owner.getUsername())).with(csrf())
                        .param("action", "import")
                        .param("validIds", main.getId().toString())
                        .param("description_" + main.getId(), "Salary Credit")
                        .param("type_" + main.getId(), "INCOME")
                        .param("category_" + main.getId(), "Salary"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("successMessage"));

        var importedIncome = incomeService.getUserIncome(owner);
        assertEquals(1, importedIncome.size());
        assertEquals("Salary Credit", importedIncome.get(0).getDescription());
        assertEquals(new BigDecimal("2500.00"), importedIncome.get(0).getAmount());
        assertEquals(LocalDate.of(2026, 9, 1), importedIncome.get(0).getIncomeDate());
        assertEquals("Salary", importedIncome.get(0).getCategory());
        assertEquals("IMPORT", importedIncome.get(0).getIncomeSource());
        assertTrue(importedIncome.get(0).getNotes().contains("statement.csv"));

        mockMvc.perform(post("/import/validate").with(user(owner.getUsername())).with(csrf())
                        .param("action", "import")
                        .param("validIds", duplicate.getId().toString())
                        .param("description_" + duplicate.getId(), "Salary Deposit")
                        .param("type_" + duplicate.getId(), "INCOME")
                        .param("category_" + duplicate.getId(), "Salary"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(1, incomeService.getUserIncome(owner).size());

        mockMvc.perform(get("/import/preview").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Salary Credit")))
                .andExpect(content().string(not(containsString("Salary Deposit"))));
    }

    @Test
    void committedImportRowsAppearInImportIncomeAndExpenses() throws Exception {
        User owner = register("import-visible-owner");
        ImportedTransaction expense = saveImported(owner, "Imported expense", new BigDecimal("19.95"),
                LocalDate.of(2026, 9, 4), "EXPENSE", "Food");
        ImportedTransaction income = saveImported(owner, "Imported income", new BigDecimal("750"),
                LocalDate.of(2026, 9, 5), "INCOME", "Salary");
        incomeService.addIncome(owner, "Manual salary", new BigDecimal("1000"),
                LocalDate.of(2026, 9, 10), "Salary", "Employer", "Manual entry");
        expenseService.addExpense(owner, "Manual food", new BigDecimal("12"),
                LocalDate.of(2026, 9, 10), "Food", "Card", "Manual entry", null);

        mockMvc.perform(post("/import/validate").with(user(owner.getUsername())).with(csrf())
                        .param("action", "import")
                        .param("validIds", expense.getId().toString(), income.getId().toString())
                        .param("description_" + expense.getId(), "Imported expense")
                        .param("type_" + expense.getId(), "EXPENSE")
                        .param("category_" + expense.getId(), "Food")
                        .param("description_" + income.getId(), "Imported income")
                        .param("type_" + income.getId(), "INCOME")
                        .param("category_" + income.getId(), "Salary"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(get("/import/preview").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported expense")))
                .andExpect(content().string(containsString("Imported income")))
                .andExpect(content().string(containsString(">IMPORTED<")))
                .andExpect(content().string(containsString("href=\"/import/history\"")));
        mockMvc.perform(get("/import/history").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported Income (1)")))
                .andExpect(content().string(containsString("Imported Expenses (1)")))
                .andExpect(content().string(containsString("Imported expense")))
                .andExpect(content().string(containsString("Imported income")))
                .andExpect(content().string(containsString("Imported from statement.csv")))
                .andExpect(content().string(not(containsString("Manual salary"))))
                .andExpect(content().string(not(containsString("Manual food"))));
        mockMvc.perform(get("/expenses").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported expense")));
        mockMvc.perform(get("/income").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported income")));
    }

    @Test
    void cancellingTheImportPreviewLeavesRecordsUnimported() throws Exception {
        User owner = register("import-cancel-owner");
        ImportedTransaction income = saveImported(owner, "Refund", new BigDecimal("40"),
                LocalDate.of(2026, 9, 2), "INCOME", "Other");

        mockMvc.perform(post("/import/validate").with(user(owner.getUsername())).with(csrf())
                        .param("action", "cancel")
                        .param("validIds", income.getId().toString()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/import"));

        assertTrue(incomeService.getUserIncome(owner).isEmpty());
        assertEquals("PARSED",
                importRepository.findByIdAndUser(income.getId(), owner).orElseThrow().getImportStatus());
    }

    private ImportedTransaction saveImported(User user, String description, BigDecimal amount,
                                             LocalDate date, String type, String category) {
        ImportedTransaction record = new ImportedTransaction();
        record.setUser(user);
        record.setOriginalFilename("statement.csv");
        record.setFileType("CSV");
        record.setTransactionDate(date);
        record.setDescription(description);
        record.setTransactionType(type);
        record.setAmount(amount);
        record.setCategory(category);
        record.setImportStatus("PARSED");
        return importFileService.saveImportedTransaction(record);
    }

    private User register(String prefix) {
        String suffix = Long.toString(System.nanoTime());
        return userService.registerUser(prefix + "_" + suffix, prefix + "_" + suffix + "@local.test",
                "secret123", prefix);
    }
}
