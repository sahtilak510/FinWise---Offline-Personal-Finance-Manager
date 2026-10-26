package com.finance.features.repository;

import com.finance.features.model.TrackedAsset;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedAssetRepository extends JpaRepository<TrackedAsset, Long> {
    List<TrackedAsset> findByUserOrderByNameAsc(User user);
    Optional<TrackedAsset> findByIdAndUser(Long id, User user);
}
