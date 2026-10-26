package com.finance.features.repository;

import com.finance.features.model.Subscription;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserOrderByNextRenewalAsc(User user);
    Optional<Subscription> findByIdAndUser(Long id, User user);
}
