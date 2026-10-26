package com.finance.features.repository;

import com.finance.features.model.Household;
import com.finance.features.model.HouseholdMember;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {
    List<HouseholdMember> findByUserOrderByHouseholdNameAsc(User user);
    List<HouseholdMember> findByHouseholdOrderByUserUsernameAsc(Household household);
    Optional<HouseholdMember> findByHouseholdAndUser(Household household, User user);
    Optional<HouseholdMember> findByIdAndHouseholdOwner(Long id, User owner);
    void deleteByHouseholdAndUser(Household household, User user);
}
