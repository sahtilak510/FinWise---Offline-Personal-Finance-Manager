package com.finance.features.repository;

import com.finance.features.model.Household;
import com.finance.features.model.HouseholdExpense;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HouseholdExpenseRepository extends JpaRepository<HouseholdExpense, Long> {
    List<HouseholdExpense> findByHouseholdOrderByExpenseDateDesc(Household household);
    List<HouseholdExpense> findByPaidBy(User user);
    Optional<HouseholdExpense> findByIdAndHousehold(Long id, Household household);
}
