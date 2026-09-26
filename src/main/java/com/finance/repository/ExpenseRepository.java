package com.finance.repository;

import com.finance.model.entity.Expense;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByUserOrderByExpenseDateDesc(User user);

    Optional<Expense> findByIdAndUser(Long id, User user);
    
    List<Expense> findByUserAndExpenseDateBetween(User user, LocalDate startDate, LocalDate endDate);
    
    List<Expense> findByUserAndCategory(User user, String category);
    
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user = :user AND e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpensesByDateRange(@Param("user") User user, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user = :user AND e.category = :category")
    BigDecimal getTotalExpensesByCategory(@Param("user") User user, @Param("category") String category);
}
