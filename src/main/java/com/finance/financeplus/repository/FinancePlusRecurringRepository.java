package com.finance.financeplus.repository;

import com.finance.financeplus.model.FinancePlusRecurring;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancePlusRecurringRepository extends JpaRepository<FinancePlusRecurring, Long> {
    List<FinancePlusRecurring> findByUserOrderByNextDueDateAsc(User user);
    List<FinancePlusRecurring> findByUserAndActiveTrueOrderByNextDueDateAsc(User user);
    List<FinancePlusRecurring> findByActiveTrueAndAutoPostTrueAndNextDueDateLessThanEqual(LocalDate dueDate);
    Optional<FinancePlusRecurring> findByIdAndUser(Long id, User user);
    long countByUserAndActiveTrue(User user);
}
