package com.finance.features.repository;

import com.finance.features.model.InvestmentHolding;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvestmentHoldingRepository extends JpaRepository<InvestmentHolding, Long> {
    List<InvestmentHolding> findByUserOrderByNameAsc(User user);
    Optional<InvestmentHolding> findByIdAndUser(Long id, User user);
}
