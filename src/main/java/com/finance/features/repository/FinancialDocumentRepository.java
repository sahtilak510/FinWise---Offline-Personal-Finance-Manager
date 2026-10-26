package com.finance.features.repository;

import com.finance.features.model.FinancialDocument;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialDocumentRepository extends JpaRepository<FinancialDocument, Long> {
    List<FinancialDocument> findByUserOrderByCreatedAtDesc(User user);
    Optional<FinancialDocument> findByIdAndUser(Long id, User user);
}
