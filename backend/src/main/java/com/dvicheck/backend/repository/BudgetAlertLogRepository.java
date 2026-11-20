package com.dvicheck.backend.repository;

import java.util.List;
import java.time.Instant;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.dvicheck.backend.model.BudgetAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BudgetAlertLogRepository extends JpaRepository<BudgetAlertLog, UUID> {

    boolean existsByUserIdAndThresholdAndPeriodMonth(UUID userId, Integer threshold, String periodMonth);

    boolean existsByUserIdAndAlertTypeAndThresholdAndPeriodMonth(
        UUID userId, String alertType, Integer threshold, String periodMonth);

    // Admin overview: [alertType, threshold, count] for alerts logged since the given instant
    @Query("select l.alertType, l.threshold, count(l) from BudgetAlertLog l where l.sentAt >= :since "
         + "group by l.alertType, l.threshold order by l.alertType, l.threshold")
    List<Object[]> countSentSinceGrouped(@Param("since") Instant since);
}
