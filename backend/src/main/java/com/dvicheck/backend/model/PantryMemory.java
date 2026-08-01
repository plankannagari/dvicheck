package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pantry_memory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PantryMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "normalised_name", nullable = false, length = 200)
    private String normalisedName;

    @Column(name = "last_bought_date", nullable = false)
    private LocalDate lastBoughtDate;

    @Column(name = "typical_quantity", length = 50)
    private String typicalQuantity;

    @Builder.Default
    @Column(name = "estimated_remaining_days")
    private Integer estimatedRemainingDays = 0;

    @Builder.Default
    @Column(name = "purchase_count")
    private Integer purchaseCount = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (estimatedRemainingDays == null) {
            estimatedRemainingDays = 0;
        }
        if (purchaseCount == null) {
            purchaseCount = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static String normalise(String itemName) {
        return itemName.toLowerCase().trim().replaceAll("\\s+", " ");
    }
}
