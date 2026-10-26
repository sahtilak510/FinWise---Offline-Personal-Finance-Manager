package com.finance.features.model;

import com.finance.model.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "feature_investments")
@Getter
@Setter
public class InvestmentHolding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String ticker;

    @Column(name = "investment_type", nullable = false, length = 40)
    private String investmentType;

    @Column(nullable = false, precision = 16, scale = 6)
    private BigDecimal units;

    @Column(name = "cost_basis", nullable = false, precision = 12, scale = 2)
    private BigDecimal costBasis;

    @Column(name = "current_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
