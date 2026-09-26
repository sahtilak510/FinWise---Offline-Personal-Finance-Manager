package com.finance.service;

import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.User;
import com.finance.repository.FinancialGoalRepository;
import com.finance.util.FinanceValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FinancialGoalService {
    private final FinancialGoalRepository financialGoalRepository;

    public FinancialGoalService(FinancialGoalRepository financialGoalRepository) {
        this.financialGoalRepository = financialGoalRepository;
    }

    public FinancialGoal createGoal(User user, String goalName, String description, BigDecimal targetAmount,
                                    LocalDate targetDate, String priority) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        FinancialGoal goal = new FinancialGoal();
        goal.setUser(user);
        goal.setGoalName(FinanceValidation.requireText(goalName, "Goal name", 150));
        goal.setDescription(FinanceValidation.optionalText(description, "Goal description", 500));
        goal.setTargetAmount(FinanceValidation.requirePositiveAmount(targetAmount, "Goal target amount"));
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setTargetDate(FinanceValidation.requireDate(targetDate, "Goal target date"));
        goal.setPriority(FinanceValidation.requireText(priority, "Goal priority", 50));
        goal.setGoalStatus("IN_PROGRESS");

        return financialGoalRepository.save(goal);
    }

    public List<FinancialGoal> getUserGoals(User user) {
        return financialGoalRepository.findByUserOrderByTargetDateAsc(user);
    }

    public List<FinancialGoal> getGoalsByStatus(User user, String goalStatus) {
        return financialGoalRepository.findByUserAndGoalStatus(user, goalStatus);
    }

    public List<FinancialGoal> getGoalsByPriority(User user, String priority) {
        return financialGoalRepository.findByUserAndPriority(user, priority);
    }

    public Optional<FinancialGoal> getGoalById(Long id) {
        return financialGoalRepository.findById(id);
    }

    public Optional<FinancialGoal> getGoalById(Long id, User user) {
        return financialGoalRepository.findByIdAndUser(id, user);
    }

    public FinancialGoal updateGoal(User user, Long id, String goalName, String description, BigDecimal targetAmount,
                                    LocalDate targetDate, String priority) {
        FinancialGoal goal = financialGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        goal.setGoalName(FinanceValidation.requireText(goalName, "Goal name", 150));
        goal.setDescription(FinanceValidation.optionalText(description, "Goal description", 500));
        goal.setTargetAmount(FinanceValidation.requirePositiveAmount(targetAmount, "Goal target amount"));
        goal.setTargetDate(FinanceValidation.requireDate(targetDate, "Goal target date"));
        goal.setPriority(FinanceValidation.requireText(priority, "Goal priority", 50));

        return financialGoalRepository.save(goal);
    }

    public FinancialGoal updateGoal(Long id, String goalName, String description, BigDecimal targetAmount,
                                    LocalDate targetDate, String priority) {
        FinancialGoal goal = financialGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        goal.setGoalName(FinanceValidation.requireText(goalName, "Goal name", 150));
        goal.setDescription(FinanceValidation.optionalText(description, "Goal description", 500));
        goal.setTargetAmount(FinanceValidation.requirePositiveAmount(targetAmount, "Goal target amount"));
        goal.setTargetDate(FinanceValidation.requireDate(targetDate, "Goal target date"));
        goal.setPriority(FinanceValidation.requireText(priority, "Goal priority", 50));

        return financialGoalRepository.save(goal);
    }

    public FinancialGoal updateGoalProgress(User user, Long id, BigDecimal currentAmount) {
        FinancialGoal goal = financialGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        BigDecimal validatedAmount = FinanceValidation.requireNonNegativeAmount(currentAmount, "Goal progress");
        goal.setCurrentAmount(validatedAmount);

        if (validatedAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setGoalStatus("COMPLETED");
        } else if (!"COMPLETED".equals(goal.getGoalStatus())) {
            goal.setGoalStatus("IN_PROGRESS");
        }

        return financialGoalRepository.save(goal);
    }

    public FinancialGoal updateGoalProgress(Long id, BigDecimal currentAmount) {
        FinancialGoal goal = financialGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        BigDecimal validatedAmount = FinanceValidation.requireNonNegativeAmount(currentAmount, "Goal progress");
        goal.setCurrentAmount(validatedAmount);

        if (validatedAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setGoalStatus("COMPLETED");
        } else if (!"COMPLETED".equals(goal.getGoalStatus())) {
            goal.setGoalStatus("IN_PROGRESS");
        }

        return financialGoalRepository.save(goal);
    }

    public void markGoalAsCompleted(User user, Long id) {
        FinancialGoal goal = financialGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        goal.setGoalStatus("COMPLETED");
        financialGoalRepository.save(goal);
    }

    public void markGoalAsCompleted(Long id) {
        FinancialGoal goal = financialGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        goal.setGoalStatus("COMPLETED");
        financialGoalRepository.save(goal);
    }

    public void markGoalAsFailed(User user, Long id) {
        FinancialGoal goal = financialGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        goal.setGoalStatus("FAILED");
        financialGoalRepository.save(goal);
    }

    public void markGoalAsFailed(Long id) {
        FinancialGoal goal = financialGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        goal.setGoalStatus("FAILED");
        financialGoalRepository.save(goal);
    }

    public void deleteGoal(User user, Long id) {
        FinancialGoal goal = financialGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        financialGoalRepository.delete(goal);
    }

    public void deleteGoal(Long id) {
        financialGoalRepository.deleteById(id);
    }
}
