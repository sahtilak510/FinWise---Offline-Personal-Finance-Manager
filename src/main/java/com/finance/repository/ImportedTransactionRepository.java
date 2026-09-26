package com.finance.repository;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportedTransactionRepository extends JpaRepository<ImportedTransaction, Long> {

    List<ImportedTransaction> findByUserOrderByTransactionDateDesc(User user);

    List<ImportedTransaction> findByUserAndFileType(User user, String fileType);

    List<ImportedTransaction> findByUserAndImportStatus(User user, String importStatus);

    @Query("SELECT SUM(i.amount) FROM ImportedTransaction i WHERE i.user = :user AND i.transactionType = :type")
    BigDecimal getTotalImportedAmountByType(@Param("user") User user, @Param("type") String type);

    @Query("SELECT COUNT(i) FROM ImportedTransaction i WHERE i.user = :user AND i.importStatus = :status")
    long countByUserAndImportStatus(@Param("user") User user, @Param("status") String status);

    @Query("SELECT MAX(i.transactionDate) FROM ImportedTransaction i WHERE i.user = :user")
    LocalDate getMaxTransactionDate(@Param("user") User user);

    Optional<ImportedTransaction> findByIdAndUser(Long id, User user);

    boolean existsBySourceImagePathAndUserAndIdNot(String sourceImagePath, User user, Long id);
}