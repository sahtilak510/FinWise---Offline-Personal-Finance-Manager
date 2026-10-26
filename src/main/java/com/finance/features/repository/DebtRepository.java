package com.finance.features.repository;

import com.finance.features.model.Debt;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findByUserOrderByDueDateAsc(User user);
    Optional<Debt> findByIdAndUser(Long id, User user);
}
