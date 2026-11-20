package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// @DynamicUpdate: UPDATEs write only the changed columns. Without it every flush writes
// every column, so a webhook (subscription_*) and a referral reward (referral_pro_until)
// overlapping on the same user could silently overwrite each other's value (Day 37).
@Entity
@DynamicUpdate
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Column(name = "household_size")
    private Integer householdSize = 1;

    @Builder.Default
    @Column(length = 3)
    private String currency = "USD";

    @Builder.Default
    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = true;

    @Column(name = "push_token", length = 200)
    private String pushToken;

    @Column(name = "budget_amount", precision = 10, scale = 2)
    private BigDecimal budgetAmount;

    @Builder.Default
    @Column(name = "onboarding_completed", nullable = false)
    private Boolean onboardingCompleted = false;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Column(name = "subscription_product_id")
    private String subscriptionProductId;

    // Pro time earned through referrals (V15). Deliberately separate from
    // subscriptionExpiresAt, which the RevenueCat webhook owns and overwrites.
    @Column(name = "referral_pro_until")
    private Instant referralProUntil;

    // Invite code, generated lazily on first request (V15, UNIQUE)
    @Column(name = "referral_code")
    private String referralCode;

    // Comma-separated percentages (e.g. "50,75,90,100"); null = default thresholds
    @Column(name = "alert_thresholds")
    private String alertThresholds;

    // Primitive, so never null — no @Builder.Default or @PrePersist guard needed
    // (Lombok getter is isPaceAlertsEnabled())
    @Column(name = "pace_alerts_enabled", nullable = false)
    private boolean paceAlertsEnabled;

    // THE Pro rule — the only Java implementation. Pro = an active paid subscription OR
    // active referral time. UserRepository.findProUserIds is the one query copy; keep
    // the two in sync.
    @Transient
    public boolean isPro() {
        return isProAt(Instant.now());
    }

    public boolean isProAt(Instant now) {
        return (subscriptionExpiresAt != null && subscriptionExpiresAt.isAfter(now))
            || (referralProUntil != null && referralProUntil.isAfter(now));
    }

    // "PAID" | "REFERRAL" | "NONE" — PAID wins when both are active. Not exposed in any DTO.
    public String proSource(Instant now) {
        if (subscriptionExpiresAt != null && subscriptionExpiresAt.isAfter(now)) return "PAID";
        if (referralProUntil != null && referralProUntil.isAfter(now)) return "REFERRAL";
        return "NONE";
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (householdSize == null) {
            householdSize = 1;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (notificationsEnabled == null) {
            notificationsEnabled = true;
        }
        if (onboardingCompleted == null) {
            onboardingCompleted = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
