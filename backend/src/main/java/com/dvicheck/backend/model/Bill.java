package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "store_name")
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type")
    private BillType billType;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "avoidable_amount")
    private BigDecimal avoidableAmount;

    @Builder.Default
    private String currency = "USD";

    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LineItem> lineItems = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (currency == null) {
            currency = "USD";
        }
    }
}
