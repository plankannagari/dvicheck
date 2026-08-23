package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.model.BudgetAlertLog;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BudgetAlertLogRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private static final List<Integer> THRESHOLDS = List.of(80, 100);
    private static final DateTimeFormatter MONTH_NAME_FORMAT = DateTimeFormatter.ofPattern("MMMM");

    private final MonthlyReportService monthlyReportService;
    private final UserRepository userRepository;
    private final BudgetAlertLogRepository budgetAlertLogRepository;
    private final NotificationService notificationService;

    @Transactional
    public void checkAndSendAlerts(UUID userId) {
        try {
            // Always derived from MonthlyReportService — never re-sum bills here — so
            // the numbers in the push notification always match HomeScreen.
            MonthlyReportDto report = monthlyReportService.getMonthlyReport(userId);
            if (report.budgetAmount() == null) {
                return;
            }

            int percentSpent = (int) Math.round(report.budgetUsedPercent());
            if (percentSpent < 80) {
                return;
            }

            String periodMonth = YearMonth.now().toString();

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }

            String month = report.monthStart().format(MONTH_NAME_FORMAT);

            for (int threshold : THRESHOLDS) {
                if (percentSpent < threshold) {
                    continue;
                }
                if (budgetAlertLogRepository.existsByUserIdAndThresholdAndPeriodMonth(userId, threshold, periodMonth)) {
                    continue;
                }

                String title;
                String body;
                if (threshold == 100) {
                    BigDecimal over = report.totalSpent().subtract(report.budgetAmount());
                    title = "Budget exceeded";
                    body = String.format("You've gone over your %s budget by $%.2f", month, over);
                } else {
                    title = "Approaching your budget";
                    body = String.format("You've used %d%% of your %s budget", percentSpent, month);
                }

                notificationService.sendNotification(
                    user.getPushToken(), title, body, Map.of("type", "BUDGET_ALERT"));

                BudgetAlertLog alertLog = BudgetAlertLog.builder()
                    .user(user)
                    .threshold(threshold)
                    .periodMonth(periodMonth)
                    .build();
                budgetAlertLogRepository.save(alertLog);
            }
        } catch (Exception e) {
            log.warn("Budget alert check failed for user={}", userId, e);
        }
    }
}
