package com.finance.features.repository;

import com.finance.features.model.SavingsChallenge;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsChallengeRepository extends JpaRepository<SavingsChallenge, Long> {
    List<SavingsChallenge> findByUserOrderByTargetDateAsc(User user);
    Optional<SavingsChallenge> findByIdAndUser(Long id, User user);
}
