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
@Table(name = "shopping_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id")
    private ShoppingList list;

    @Column(nullable = false, length = 200)
    private String name;

    @Builder.Default
    @Column(length = 50)
    private String quantity = "1";

    @Builder.Default
    @Column(name = "is_checked")
    private boolean isChecked = false;

    @Builder.Default
    @Column(name = "is_duplicate")
    private boolean isDuplicate = false;

    @Column(name = "duplicate_warning", length = 300)
    private String duplicateWarning;

    @Column(name = "last_purchased_date")
    private LocalDate lastPurchasedDate;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (quantity == null) {
            quantity = "1";
        }
    }
}
