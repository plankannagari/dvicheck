package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.WeeklyCoachMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyCoachMessageRepository extends JpaRepository<WeeklyCoachMessage, UUID> {

    boolean existsByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

    Optional<WeeklyCoachMessage> findFirstByUserIdAndStatusOrderByWeekStartDesc(UUID userId, String status);

    Optional<WeeklyCoachMessage> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);
}
