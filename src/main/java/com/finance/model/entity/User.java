package com.finance.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "theme", nullable = false, columnDefinition = "TEXT DEFAULT 'light'")
    private String theme = "light";

    @Column(name = "language", nullable = false, columnDefinition = "TEXT DEFAULT 'en'")
    private String language = "en";

    @Column(name = "notifications_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean notificationsEnabled = true;

    @Column(name = "budget_alerts_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean budgetAlertsEnabled = true;

    @Column(name = "daily_summary_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean dailySummaryEnabled = true;

    @Column(name = "goal_updates_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean goalUpdatesEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Boolean getNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(Boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public Boolean getBudgetAlertsEnabled() { return budgetAlertsEnabled; }
    public void setBudgetAlertsEnabled(Boolean budgetAlertsEnabled) { this.budgetAlertsEnabled = budgetAlertsEnabled; }

    public Boolean getDailySummaryEnabled() { return dailySummaryEnabled; }
    public void setDailySummaryEnabled(Boolean dailySummaryEnabled) { this.dailySummaryEnabled = dailySummaryEnabled; }

    public Boolean getGoalUpdatesEnabled() { return goalUpdatesEnabled; }
    public void setGoalUpdatesEnabled(Boolean goalUpdatesEnabled) { this.goalUpdatesEnabled = goalUpdatesEnabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}