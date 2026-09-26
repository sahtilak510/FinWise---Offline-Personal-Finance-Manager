package com.finance.financeplus.repository;

import com.finance.financeplus.model.FinancePlusBackup;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancePlusBackupRepository extends JpaRepository<FinancePlusBackup, Long> {
    List<FinancePlusBackup> findTop50ByUserOrderByCreatedAtDesc(User user);
    Optional<FinancePlusBackup> findByIdAndUser(Long id, User user);
    long countByUser(User user);
    void deleteByUser(User user);
}
