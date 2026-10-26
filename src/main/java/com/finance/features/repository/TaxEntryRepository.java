package com.finance.features.repository;

import com.finance.features.model.TaxEntry;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxEntryRepository extends JpaRepository<TaxEntry, Long> {
    List<TaxEntry> findByUserOrderByEntryDateDesc(User user);
    Optional<TaxEntry> findByIdAndUser(Long id, User user);
}
