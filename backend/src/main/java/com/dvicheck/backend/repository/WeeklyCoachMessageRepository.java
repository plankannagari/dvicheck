package com.dvicheck.backend.repository;

import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
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

    // Admin overview: [weekStart, source, count] of READY messages from the given week on
    @Query("select m.weekStart, m.source, count(m) from WeeklyCoachMessage m "
         + "where m.status = 'READY' and m.weekStart >= :fromWeek group by m.weekStart, m.source")
    List<Object[]> countReadyBySourceSince(@Param("fromWeek") LocalDate fromWeek);

    @Query("select max(m.weekStart) from WeeklyCoachMessage m where m.status = 'READY'")
    LocalDate findLatestReadyWeekStart();
}
