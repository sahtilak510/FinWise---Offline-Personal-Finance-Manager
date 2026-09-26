package com.finance.service;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionScheduler.class);

    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;

    public RecurringTransactionScheduler(UserRepository userRepository, ExpenseService expenseService,
                                         IncomeService incomeService) {
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
    }

    @Scheduled(cron = "${app.recurring-transactions.cron:0 15 0 * * *}")
    public void materializeDueTransactions() {
        for (User user : userRepository.findAll()) {
            try {
                expenseService.processDueRecurringTransactions(user, null);
                incomeService.processDueRecurringTransactions(user, null);
            } catch (RuntimeException exception) {
                log.error("Unable to process recurring transactions for user {}", user.getId(), exception);
            }
        }
    }
}
