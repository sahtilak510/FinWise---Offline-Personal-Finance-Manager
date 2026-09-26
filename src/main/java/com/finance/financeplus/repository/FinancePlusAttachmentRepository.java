package com.finance.financeplus.repository;

import com.finance.financeplus.model.FinancePlusAttachment;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancePlusAttachmentRepository extends JpaRepository<FinancePlusAttachment, Long> {
    List<FinancePlusAttachment> findByUserOrderByCreatedAtDesc(User user);
    List<FinancePlusAttachment> findTop50ByUserOrderByCreatedAtDesc(User user);
    Optional<FinancePlusAttachment> findByIdAndUser(Long id, User user);
    long countByUser(User user);
    void deleteByUser(User user);
}
