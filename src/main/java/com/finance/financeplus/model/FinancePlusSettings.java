package com.finance.financeplus.model;

import com.finance.model.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_plus_settings")
@Getter
@Setter
public class FinancePlusSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "passcode_hash")
    private String passcodeHash;

    @Column(name = "passcode_enabled", nullable = false)
    private Boolean passcodeEnabled = false;

    @Column(name = "bill_reminders_enabled", nullable = false)
    private Boolean billRemindersEnabled = true;

    @Column(name = "bill_reminder_days", nullable = false)
    private Integer billReminderDays = 3;

    @Column(name = "large_expense_alerts_enabled", nullable = false)
    private Boolean largeExpenseAlertsEnabled = true;

    @Column(name = "large_expense_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal largeExpenseThreshold = new BigDecimal("1000.00");

    @Column(name = "weekly_summary_enabled", nullable = false)
    private Boolean weeklySummaryEnabled = true;

    @Column(name = "monthly_summary_enabled", nullable = false)
    private Boolean monthlySummaryEnabled = true;

    @Column(name = "auto_backup_enabled", nullable = false)
    private Boolean autoBackupEnabled = false;

    @Column(name = "automatic_backup_hour", nullable = false)
    private Integer automaticBackupHour = 2;

    @Column(name = "vault_salt")
    private String vaultSalt;

    @Column(name = "passcode_updated_at")
    private LocalDateTime passcodeUpdatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
