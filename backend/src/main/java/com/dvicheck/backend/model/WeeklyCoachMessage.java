package com.dvicheck.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// @Getter/@Setter rather than @Data, same as BudgetAlertLog/ItemFeedback: @Data's
// equals/hashCode/toString would traverse the lazy `user` association.
@Entity
@Table(name = "weekly_coach_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyCoachMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    private String status;

    private String source;

    private String headline;

    private String win;

    private String watch;

    private String action;

    @Column(name = "facts_json", columnDefinition = "TEXT")
    private String factsJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
