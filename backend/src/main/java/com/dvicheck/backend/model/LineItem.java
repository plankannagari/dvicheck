package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "line_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @Column(nullable = false, length = 200)
    private String name;

    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ItemCategory category = ItemCategory.ESSENTIAL;

    @Column(name = "flag_reason", length = 300)
    private String flagReason;

    @Column(length = 500)
    private String suggestion;

    @Column(name = "saving_estimate")
    private BigDecimal savingEstimate;

    @Builder.Default
    private BigDecimal confidence = new BigDecimal("0.00");

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        if (category == null) {
            category = ItemCategory.ESSENTIAL;
        }
        if (confidence == null) {
            confidence = new BigDecimal("0.00");
        }
    }
}
