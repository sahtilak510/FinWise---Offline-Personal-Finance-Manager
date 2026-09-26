package com.finance.repository;

import com.finance.model.entity.Budget;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserAndIsActiveTrue(User user);

    Optional<Budget> findByIdAndUser(Long id, User user);
    
    List<Budget> findByUser(User user);
    
    Optional<Budget> findByUserAndCategoryAndBudgetMonth(User user, String category, String budgetMonth);
}
