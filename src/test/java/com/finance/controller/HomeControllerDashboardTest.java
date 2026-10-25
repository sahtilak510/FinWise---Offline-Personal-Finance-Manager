package com.finance.controller;

import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.AccountService;
import com.finance.service.BudgetService;
import com.finance.service.ExpenseService;
import com.finance.service.FinancialGoalService;
import com.finance.service.ImportFileService;
import com.finance.service.IncomeService;
import com.finance.service.UserDataExportService;
import com.finance.service.UserService;
import com.finance.service.excel.FinancialHealthScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeControllerDashboardTest {

    @Test
    void dashboardTotalsIncludeTransactionsFromAnyDate() {
        User user = new User();
        user.setUsername("dashboard-user");
        Income income = new Income();
        income.setAmount(new BigDecimal("1250"));
        income.setIncomeDate(LocalDate.of(2026, 9, 19));
        Expense expense = new Expense();
        expense.setAmount(new BigDecimal("45"));
        expense.setExpenseDate(LocalDate.of(2026, 9, 20));

        UserRepository userRepository = mock(UserRepository.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        IncomeService incomeService = mock(IncomeService.class);
        BudgetService budgetService = mock(BudgetService.class);
        FinancialGoalService financialGoalService = mock(FinancialGoalService.class);
        ImportFileService importFileService = mock(ImportFileService.class);
        FinancialHealthScoreService healthScoreService = mock(FinancialHealthScoreService.class);
        when(userRepository.findByUsername("dashboard-user")).thenReturn(Optional.of(user));
        when(incomeService.getUserIncome(user)).thenReturn(List.of(income));
        when(expenseService.getUserExpenses(user)).thenReturn(List.of(expense));
        when(budgetService.getUserActiveBudgets(user)).thenReturn(List.of());
        when(financialGoalService.getUserGoals(user)).thenReturn(List.of());
        when(importFileService.getPendingImportedTransactions(user)).thenReturn(List.of());
        when(healthScoreService.calculateHealthScore(user)).thenReturn(
                new FinancialHealthScoreService.FinancialHealthScore(
                        0, "N/A", List.of(), 0, 0, 0, 0, 0));

        HomeController controller = new HomeController(userRepository, mock(UserService.class),
                mock(AccountService.class), expenseService, incomeService, budgetService,
                financialGoalService, importFileService, mock(UserDataExportService.class),
                healthScoreService);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("dashboard-user");
        Model model = mock(Model.class);

        controller.dashboard(authentication, model);

        verify(model).addAttribute("totalIncome", new BigDecimal("1250"));
        verify(model).addAttribute("totalExpenses", new BigDecimal("45"));
    }
}
