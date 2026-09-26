package com.finance.financeplus.service;

import com.finance.financeplus.model.FinancePlusRecurring;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.IncomeRepository;
import com.finance.service.ExpenseService;
import com.finance.service.IncomeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancePlusRecurringServiceTest {

    @Test
    void processDueCreatesExpenseAndAdvancesNextDate() {
        FinancePlusRecurringRepository repository = mock(FinancePlusRecurringRepository.class);
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        IncomeRepository incomeRepository = mock(IncomeRepository.class);
        ExpenseService expenseService = new ExpenseService(expenseRepository);
        IncomeService incomeService = new IncomeService(incomeRepository);
        FinancePlusRecurringService service = new FinancePlusRecurringService(repository, expenseService, incomeService);
        User user = user();
        FinancePlusRecurring rule = new FinancePlusRecurring();
        rule.setUser(user);
        rule.setTransactionType("EXPENSE");
        rule.setDescription("Rent");
        rule.setAmount(new BigDecimal("500"));
        rule.setCategory("Housing");
        rule.setPaymentMethod("Bank");
        rule.setFrequency("MONTHLY");
        rule.setNextDueDate(LocalDate.of(2026, 9, 1));
        rule.setActive(true);
        rule.setAutoPost(true);
        when(repository.findByActiveTrueAndAutoPostTrueAndNextDueDateLessThanEqual(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(rule));
        when(expenseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int created = service.processDue(LocalDate.of(2026, 10, 1));

        assertEquals(2, created);
        assertEquals(LocalDate.of(2026, 11, 1), rule.getNextDueDate());
        verify(expenseRepository, org.mockito.Mockito.times(2)).save(any());
        verify(repository).save(rule);
    }

    @Test
    void invalidAmountIsRejected() {
        FinancePlusRecurringService service = new FinancePlusRecurringService(
                mock(FinancePlusRecurringRepository.class),
                new ExpenseService(mock(ExpenseRepository.class)),
                new IncomeService(mock(IncomeRepository.class)));

        assertThrows(IllegalArgumentException.class, () -> service.create(user(), "EXPENSE", "Bad",
                BigDecimal.ZERO, "Other", "Cash", "MONTHLY", LocalDate.now(), true));
    }

    private User user() {
        User user = new User();
        user.setId(9L);
        user.setUsername("recurring-user");
        return user;
    }
}
