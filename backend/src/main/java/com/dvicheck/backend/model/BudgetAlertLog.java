package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget_alert_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer threshold;

    @Column(name = "period_month", nullable = false)
    private String periodMonth;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    void prePersist() {
        this.sentAt = Instant.now();
    }
}
