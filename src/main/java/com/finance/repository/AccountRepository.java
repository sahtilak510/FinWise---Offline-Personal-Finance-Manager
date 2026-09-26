package com.finance.repository;

import com.finance.model.entity.Account;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserOrderByCreatedAtDesc(User user);
    List<Account> findByUserAndIsActiveTrueOrderByAccountNameAsc(User user);
    Optional<Account> findByIdAndUser(Long id, User user);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.user = :user AND a.isActive = true")
    BigDecimal getTotalBalance(@Param("user") User user);

    long countByUserAndIsActiveTrue(User user);
}
