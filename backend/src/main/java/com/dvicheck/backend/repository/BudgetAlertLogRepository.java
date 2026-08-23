package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.BudgetAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BudgetAlertLogRepository extends JpaRepository<BudgetAlertLog, UUID> {

    boolean existsByUserIdAndThresholdAndPeriodMonth(UUID userId, Integer threshold, String periodMonth);
}
