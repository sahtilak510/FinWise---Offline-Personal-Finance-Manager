package com.finance.financeplus.model;

import com.finance.model.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "finance_plus_notifications", indexes = {
        @Index(name = "idx_fp_notification_dedupe", columnList = "deduplication_key", unique = true)
})
@Getter
@Setter
public class FinancePlusNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, length = 10)
    private String priority = "LOW";

    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @Column(name = "deduplication_key", nullable = false, length = 200)
    private String deduplicationKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
