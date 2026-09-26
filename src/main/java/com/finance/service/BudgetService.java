package com.finance.service;

import com.finance.model.entity.Budget;
import com.finance.model.entity.User;
import com.finance.repository.BudgetRepository;
import com.finance.util.FinanceValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BudgetService {
    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public Budget createBudget(User user, String category, BigDecimal limitAmount, String budgetMonth, String notes) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(FinanceValidation.requireText(category, "Budget category", 100));
        budget.setLimitAmount(FinanceValidation.requirePositiveAmount(limitAmount, "Budget limit"));
        budget.setBudgetMonth(FinanceValidation.requireText(budgetMonth, "Budget month", 12));
        budget.setNotes(FinanceValidation.optionalText(notes, "Budget notes", 500));
        budget.setIsActive(true);
        budget.setSpentAmount(BigDecimal.ZERO);

        return budgetRepository.save(budget);
    }

    public List<Budget> getUserActiveBudgets(User user) {
        return budgetRepository.findByUserAndIsActiveTrue(user);
    }

    public List<Budget> getUserAllBudgets(User user) {
        return budgetRepository.findByUser(user);
    }

    public Optional<Budget> getBudgetById(Long id) {
        return budgetRepository.findById(id);
    }

    public Optional<Budget> getBudgetById(Long id, User user) {
        return budgetRepository.findByIdAndUser(id, user);
    }

    public Optional<Budget> getBudgetByUserAndCategoryAndMonth(User user, String category, String budgetMonth) {
        return budgetRepository.findByUserAndCategoryAndBudgetMonth(user, category, budgetMonth);
    }

    public Budget updateBudget(User user, Long id, BigDecimal limitAmount, BigDecimal spentAmount, String notes) {
        Budget budget = budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        budget.setLimitAmount(FinanceValidation.requirePositiveAmount(limitAmount, "Budget limit"));
        budget.setSpentAmount(FinanceValidation.requireNonNegativeAmount(spentAmount, "Budget spent amount"));
        budget.setNotes(FinanceValidation.optionalText(notes, "Budget notes", 500));

        return budgetRepository.save(budget);
    }

    public Budget updateBudget(Long id, BigDecimal limitAmount, BigDecimal spentAmount, String notes) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        budget.setLimitAmount(FinanceValidation.requirePositiveAmount(limitAmount, "Budget limit"));
        budget.setSpentAmount(FinanceValidation.requireNonNegativeAmount(spentAmount, "Budget spent amount"));
        budget.setNotes(FinanceValidation.optionalText(notes, "Budget notes", 500));

        return budgetRepository.save(budget);
    }

    public void deleteBudget(User user, Long id) {
        Budget budget = budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        budgetRepository.delete(budget);
    }

    public void deleteBudget(Long id) {
        budgetRepository.deleteById(id);
    }

    public Budget updateSpentAmount(User user, Long id, BigDecimal spentAmount) {
        Budget budget = budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        budget.setSpentAmount(FinanceValidation.requireNonNegativeAmount(spentAmount, "Budget spent amount"));
        return budgetRepository.save(budget);
    }

    public Budget updateSpentAmount(Long budgetId, BigDecimal spentAmount) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        budget.setSpentAmount(FinanceValidation.requireNonNegativeAmount(spentAmount, "Budget spent amount"));
        return budgetRepository.save(budget);
    }

    public void toggleBudgetActive(User user, Long id) {
        Budget budget = budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        budget.setIsActive(!budget.getIsActive());
        budgetRepository.save(budget);
    }

    public void toggleBudgetActive(Long id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        budget.setIsActive(!budget.getIsActive());
        budgetRepository.save(budget);
    }
}
