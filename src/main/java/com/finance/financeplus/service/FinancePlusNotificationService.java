package com.finance.financeplus.service;

import com.finance.financeplus.model.FinancePlusNotification;
import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.financeplus.repository.FinancePlusNotificationRepository;
import com.finance.model.entity.Expense;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.service.ExpenseService;
import com.finance.service.IncomeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@Transactional
public class FinancePlusNotificationService {

    private final FinancePlusNotificationRepository notificationRepository;
    private final FinancePlusSettingsService settingsService;
    private final FinancePlusRecurringService recurringService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;

    public FinancePlusNotificationService(FinancePlusNotificationRepository notificationRepository,
                                           FinancePlusSettingsService settingsService,
                                           FinancePlusRecurringService recurringService,
                                           ExpenseService expenseService,
                                           IncomeService incomeService) {
        this.notificationRepository = notificationRepository;
        this.settingsService = settingsService;
        this.recurringService = recurringService;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
    }

    public List<FinancePlusNotification> getNotifications(User user) {
        return notificationRepository.findTop50ByUserOrderByCreatedAtDesc(user);
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    public void markRead(User user, Long id) {
        FinancePlusNotification notification = notificationRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public void markAllRead(User user) {
        for (FinancePlusNotification notification : notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user)) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    public void generateForUser(User user) {
        FinancePlusSettings settings = settingsService.getSettings(user);
        LocalDate today = LocalDate.now();
        if (Boolean.TRUE.equals(settings.getBillRemindersEnabled())) {
            generateBillReminders(user, today, settings.getBillReminderDays());
        }
        if (Boolean.TRUE.equals(settings.getLargeExpenseAlertsEnabled())) {
            generateLargeExpenseAlerts(user, today, settings.getLargeExpenseThreshold());
        }
        if (Boolean.TRUE.equals(settings.getWeeklySummaryEnabled())) {
            generateWeeklySummary(user, today);
        }
        if (Boolean.TRUE.equals(settings.getMonthlySummaryEnabled())) {
            generateMonthlySummary(user, today);
        }
    }

    private void generateBillReminders(User user, LocalDate today, int reminderDays) {
        LocalDate limit = today.plusDays(Math.max(1, reminderDays));
        recurringService.getActiveRules(user).stream()
                .filter(rule -> !rule.getNextDueDate().isBefore(today) && !rule.getNextDueDate().isAfter(limit))
                .forEach(rule -> saveIfNew(user, "BILL_REMINDER:" + rule.getId() + ":" + rule.getNextDueDate(),
                        "HIGH", "Upcoming " + rule.getTransactionType().toLowerCase(),
                        rule.getDescription() + " is due on " + rule.getNextDueDate() + " for "
                                + rule.getAmount().setScale(2, RoundingMode.HALF_UP)));
    }

    private void generateLargeExpenseAlerts(User user, LocalDate today, BigDecimal threshold) {
        expenseService.getUserExpenses(user).stream()
                .filter(expense -> today.equals(expense.getExpenseDate()))
                .filter(expense -> expense.getAmount().compareTo(threshold) >= 0)
                .forEach(expense -> saveIfNew(user, "LARGE_EXPENSE:" + expense.getId(),
                        "HIGH", "Large expense detected",
                        expense.getDescription() + " was " + expense.getAmount().setScale(2, RoundingMode.HALF_UP)
                                + ", above your alert threshold."));
    }

    private void generateWeeklySummary(User user, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        BigDecimal income = totalIncome(user, monday, sunday);
        BigDecimal expenses = totalExpenses(user, monday, sunday);
        String week = monday + ":" + sunday;
        saveIfNew(user, "WEEKLY_SUMMARY:" + week, "LOW", "Weekly summary",
                "Income " + income.setScale(2, RoundingMode.HALF_UP) + " · Expenses "
                        + expenses.setScale(2, RoundingMode.HALF_UP) + " · Net "
                        + income.subtract(expenses).setScale(2, RoundingMode.HALF_UP));
    }

    private void generateMonthlySummary(User user, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        BigDecimal income = totalIncome(user, start, end);
        BigDecimal expenses = totalExpenses(user, start, end);
        saveIfNew(user, "MONTHLY_SUMMARY:" + month, "LOW", month + " summary",
                "Income " + income.setScale(2, RoundingMode.HALF_UP) + " · Expenses "
                        + expenses.setScale(2, RoundingMode.HALF_UP) + " · Net "
                        + income.subtract(expenses).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal totalIncome(User user, LocalDate start, LocalDate end) {
        return incomeService.getUserIncome(user).stream()
                .filter(income -> income.getIncomeDate() != null)
                .filter(income -> !income.getIncomeDate().isBefore(start))
                .filter(income -> !income.getIncomeDate().isAfter(end))
                .map(Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalExpenses(User user, LocalDate start, LocalDate end) {
        return expenseService.getUserExpenses(user).stream()
                .filter(expense -> expense.getExpenseDate() != null)
                .filter(expense -> !expense.getExpenseDate().isBefore(start))
                .filter(expense -> !expense.getExpenseDate().isAfter(end))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void saveIfNew(User user, String key, String priority, String title, String message) {
        String scopedKey = user.getId() + ":" + key;
        if (notificationRepository.existsByDeduplicationKey(scopedKey)) {
            return;
        }
        FinancePlusNotification notification = new FinancePlusNotification();
        notification.setUser(user);
        notification.setType(key.substring(0, key.indexOf(':')));
        notification.setDeduplicationKey(scopedKey);
        notification.setPriority(priority);
        notification.setTitle(title);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }
}
