package com.finance.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "income")
@NoArgsConstructor
@AllArgsConstructor
public class Income {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Column(nullable = false)
    private String category;

    @Column(name = "income_source")
    private String incomeSource;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_recurring", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT 0")
    private boolean recurring;

    @Column(name = "recurrence_frequency", length = 20)
    private String recurrenceFrequency;

    @Column(name = "next_occurrence")
    private LocalDate nextOccurrence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getIncomeSource() { return incomeSource; }
    public void setIncomeSource(String incomeSource) { this.incomeSource = incomeSource; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public String getRecurrenceFrequency() { return recurrenceFrequency; }
    public void setRecurrenceFrequency(String recurrenceFrequency) { this.recurrenceFrequency = recurrenceFrequency; }
    public LocalDate getNextOccurrence() { return nextOccurrence; }
    public void setNextOccurrence(LocalDate nextOccurrence) { this.nextOccurrence = nextOccurrence; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}