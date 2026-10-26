package com.finance.features.repository;

import com.finance.features.model.Household;
import com.finance.features.model.SharedBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SharedBudgetRepository extends JpaRepository<SharedBudget, Long> {
    List<SharedBudget> findByHouseholdOrderByCreatedAtDesc(Household household);
}
