package com.finance.repository;

import com.finance.model.entity.Income;
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
public interface IncomeRepository extends JpaRepository<Income, Long> {
    List<Income> findByUserOrderByIncomeDateDesc(User user);

    Optional<Income> findByIdAndUser(Long id, User user);
    
    List<Income> findByUserAndIncomeDateBetween(User user, LocalDate startDate, LocalDate endDate);
    
    List<Income> findByUserAndCategory(User user, String category);
    
    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.user = :user AND i.incomeDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalIncomeByDateRange(@Param("user") User user, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.user = :user AND i.category = :category")
    BigDecimal getTotalIncomeByCategory(@Param("user") User user, @Param("category") String category);
}
