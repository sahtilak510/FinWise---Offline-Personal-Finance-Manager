package com.finance.financeplus.repository;

import com.finance.financeplus.model.FinancePlusNotification;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancePlusNotificationRepository extends JpaRepository<FinancePlusNotification, Long> {
    List<FinancePlusNotification> findTop50ByUserOrderByCreatedAtDesc(User user);
    List<FinancePlusNotification> findByUserAndReadFalseOrderByCreatedAtDesc(User user);
    Optional<FinancePlusNotification> findByIdAndUser(Long id, User user);
    boolean existsByDeduplicationKey(String deduplicationKey);
    long countByUserAndReadFalse(User user);
    void deleteByUser(User user);
}
