package com.finance.financeplus.repository;

import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancePlusSettingsRepository extends JpaRepository<FinancePlusSettings, Long> {
    Optional<FinancePlusSettings> findByUser(User user);
    List<FinancePlusSettings> findByAutoBackupEnabledTrue();
}
