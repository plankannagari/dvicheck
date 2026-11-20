package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Invite relationship (V15). Plain UUID columns rather than @ManyToOne — nothing here
// needs to navigate to the User, and it keeps the entity free of lazy-loading traps.
// @Getter/@Setter rather than @Data, matching the other log-style entities.
@Entity
@Table(name = "referral")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referral {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_QUALIFIED = "QUALIFIED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "referrer_id", nullable = false)
    private UUID referrerId;

    // UNIQUE in the DB: one redemption per account
    @Column(name = "referee_id", nullable = false)
    private UUID refereeId;

    @Column(nullable = false, length = 12)
    private String code;

    // "PENDING" | "QUALIFIED" (DB CHECK). Set explicitly: Hibernate writes every column
    // on insert, so the DB default would never apply.
    @Column(nullable = false, length = 12)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "qualified_at")
    private Instant qualifiedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = STATUS_PENDING;
    }
}
