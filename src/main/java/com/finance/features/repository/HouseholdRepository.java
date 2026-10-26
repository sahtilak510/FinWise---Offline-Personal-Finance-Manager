package com.finance.features.repository;

import com.finance.features.model.Household;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
    List<Household> findByOwnerOrderByNameAsc(User owner);
    Optional<Household> findByIdAndOwner(Long id, User owner);
}
