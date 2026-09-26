package com.finance.repository;

import com.finance.model.entity.FinancialGoal;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    List<FinancialGoal> findByUserOrderByTargetDateAsc(User user);

    Optional<FinancialGoal> findByIdAndUser(Long id, User user);
    
    List<FinancialGoal> findByUserAndGoalStatus(User user, String goalStatus);
    
    List<FinancialGoal> findByUserAndPriority(User user, String priority);
}
