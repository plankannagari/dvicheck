package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Append-only admin action log (V16). Plain UUID columns: target_user_id has no FK on
// purpose (the log must outlive the user), admin_id is SET NULL if the admin is deleted.
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    // Short free text. NEVER a phone number or an amount (see AdminAuditService)
    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
