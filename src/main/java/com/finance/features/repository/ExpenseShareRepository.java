package com.finance.features.repository;

import com.finance.features.model.ExpenseShare;
import com.finance.features.model.HouseholdExpense;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpenseOrderByUserUsernameAsc(HouseholdExpense expense);
    List<ExpenseShare> findByUser(User user);
}
