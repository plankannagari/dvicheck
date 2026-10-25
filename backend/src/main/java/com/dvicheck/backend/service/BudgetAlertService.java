package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.BudgetAlertSettingsDto;
import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.dto.UpdateBudgetAlertSettingsRequest;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.BudgetAlertLog;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BudgetAlertLogRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(80, 100);
    // A List (not Set.of) so it serialises in a stable ascending order as allowedThresholds
    private static final List<Integer> ALLOWED_THRESHOLDS = List.of(50, 70, 80, 90, 100);
    private static final int MAX_CUSTOM_THRESHOLDS = 4;
    private static final int PACE_MIN_DAY_OF_MONTH = 7;
    // Must match budget_alert_log's alert_type CHECK (V13); PACE rows store threshold 0
    private static final String ALERT_TYPE_THRESHOLD = "THRESHOLD";
    private static final String ALERT_TYPE_PACE = "PACE";
    private static final int PACE_THRESHOLD = 0;
    // Same payload as Day 24 — NotificationListener on mobile routes taps on this type
    private static final Map<String, Object> ALERT_DATA = Map.of("type", "BUDGET_ALERT");
    private static final DateTimeFormatter MONTH_NAME_FORMAT = DateTimeFormatter.ofPattern("MMMM");

    private final MonthlyReportService monthlyReportService;
    private final UserRepository userRepository;
    private final BudgetAlertLogRepository budgetAlertLogRepository;
    private final NotificationService notificationService;

    // Deliberately NOT @Transactional (Day 24 had it). With one wrapping transaction,
    // log-row INSERTs only hit the DB at commit — after this method returns, outside the
    // try/catch — so a unique-key race between two concurrent scans would escape as a
    // 500 on an already-saved bill. Without it, each save() commits on its own inside
    // the try, so "never throws outward" actually holds. Nothing here touches a lazy
    // association, so no surrounding transaction is needed.
    public void checkAndSendAlerts(UUID userId) {
        checkAt(userId, LocalDate.now());
    }

    // Package-private so unit tests can pass a fixed date
    void checkAt(UUID userId, LocalDate today) {
        try {
            // Always derived from MonthlyReportService — never re-sum bills here — so
            // the numbers in the push notification always match HomeScreen.
            MonthlyReportDto report = monthlyReportService.getMonthlyReport(userId);
            BigDecimal budget = report.budgetAmount();
            if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }

            YearMonth yearMonth = YearMonth.from(today);
            String period = yearMonth.toString();
            String monthName = today.format(MONTH_NAME_FORMAT);
            boolean pro = user.isPro();
            int percent = (int) Math.round(report.budgetUsedPercent());

            sendThresholdAlert(user, report, today, yearMonth, period, monthName, pro, percent);

            if (pro && user.isPaceAlertsEnabled()
                    && today.getDayOfMonth() >= PACE_MIN_DAY_OF_MONTH && percent < 100) {
                sendPaceAlert(user, report, today, yearMonth, period);
            }
        } catch (Exception e) {
            log.warn("Budget alert check failed for user={}", userId, e);
        }
    }

    private void sendThresholdAlert(User user, MonthlyReportDto report, LocalDate today, YearMonth yearMonth,
                                    String period, String monthName, boolean pro, int percent) {
        UUID userId = user.getId();
        List<Integer> crossed = effectiveThresholds(user).stream()
            .filter(t -> percent >= t)
            .filter(t -> !budgetAlertLogRepository.existsByUserIdAndAlertTypeAndThresholdAndPeriodMonth(
                userId, ALERT_TYPE_THRESHOLD, t, period))
            .toList();
        if (crossed.isEmpty()) {
            return;
        }

        // One push for the highest newly crossed threshold — a user jumping from 60% to
        // 105% in one scan gets "exceeded", not three notifications in a row
        int highest = crossed.get(crossed.size() - 1);
        BigDecimal totalSpent = report.totalSpent();
        BigDecimal budget = report.budgetAmount();

        String title;
        String body;
        if (highest == 100) {
            BigDecimal over = totalSpent.subtract(budget);
            title = "Budget exceeded";
            body = String.format("You've gone over your %s budget by $%.2f", monthName, over);
        } else {
            title = "Approaching your budget";
            body = String.format("You've used %d%% of your %s budget", percent, monthName);
            if (pro) {
                int remainingDays = yearMonth.lengthOfMonth() - today.getDayOfMonth();
                BigDecimal remaining = budget.subtract(totalSpent);
                if (remainingDays > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal perDay = remaining.divide(BigDecimal.valueOf(remainingDays), 2, RoundingMode.HALF_UP);
                    body += String.format(" - $%.2f left, about $%.2f/day for the remaining %d days",
                        remaining, perDay, remainingDays);
                }
            }
        }

        notificationService.sendNotification(user.getPushToken(), title, body, ALERT_DATA);

        // Log EVERY crossed threshold, not just the one notified — otherwise a lower one
        // (e.g. 80 after a 100 push) would fire on the next scan as a stale "approaching"
        for (int threshold : crossed) {
            saveLog(user, ALERT_TYPE_THRESHOLD, threshold, period);
        }
    }

    private void sendPaceAlert(User user, MonthlyReportDto report, LocalDate today,
                               YearMonth yearMonth, String period) {
        BigDecimal budget = report.budgetAmount();
        // Divide at higher precision first; rounding to cents before multiplying by
        // days-in-month would compound the rounding error up to ~30x
        BigDecimal projected = report.totalSpent()
            .divide(BigDecimal.valueOf(today.getDayOfMonth()), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(yearMonth.lengthOfMonth()))
            .setScale(2, RoundingMode.HALF_UP);

        if (projected.compareTo(budget) <= 0
                || budgetAlertLogRepository.existsByUserIdAndAlertTypeAndThresholdAndPeriodMonth(
                    user.getId(), ALERT_TYPE_PACE, PACE_THRESHOLD, period)) {
            return;
        }

        BigDecimal over = projected.subtract(budget);
        notificationService.sendNotification(
            user.getPushToken(),
            "On pace to overspend",
            String.format("At this rate you'll spend about $%.2f by month end ($%.2f over budget)", projected, over),
            ALERT_DATA);
        saveLog(user, ALERT_TYPE_PACE, PACE_THRESHOLD, period);
    }

    private void saveLog(User user, String alertType, int threshold, String period) {
        budgetAlertLogRepository.save(BudgetAlertLog.builder()
            .user(user)
            .alertType(alertType)
            .threshold(threshold)
            .periodMonth(period)
            .build());
    }

    @Transactional(readOnly = true)
    public BudgetAlertSettingsDto getSettings(UUID userId) {
        return toSettingsDto(findUser(userId));
    }

    @Transactional
    public BudgetAlertSettingsDto updateSettings(UUID userId, UpdateBudgetAlertSettingsRequest request) {
        User user = findUser(userId);
        if (!user.isPro()) {
            throw DvicheckException.proRequired("Custom budget alerts are a Pro feature");
        }

        List<Integer> thresholds = request.thresholds();
        if (thresholds != null) {
            if (thresholds.isEmpty() || thresholds.size() > MAX_CUSTOM_THRESHOLDS) {
                throw DvicheckException.badRequest("Choose between 1 and " + MAX_CUSTOM_THRESHOLDS + " alert thresholds");
            }
            if (thresholds.stream().anyMatch(t -> t == null || !ALLOWED_THRESHOLDS.contains(t))) {
                throw DvicheckException.badRequest("Alert thresholds must be from " + ALLOWED_THRESHOLDS);
            }
            if (thresholds.stream().distinct().count() != thresholds.size()) {
                throw DvicheckException.badRequest("Alert thresholds must not repeat");
            }
            user.setAlertThresholds(thresholds.stream().sorted()
                .map(String::valueOf).collect(Collectors.joining(",")));
        }
        if (request.paceAlertsEnabled() != null) {
            user.setPaceAlertsEnabled(request.paceAlertsEnabled());
        }

        User saved = userRepository.save(user);
        log.info("Budget alert settings updated for user {}: thresholds={}, pace={}",
            userId, saved.getAlertThresholds(), saved.isPaceAlertsEnabled());
        return toSettingsDto(saved);
    }

    // Free users always see the defaults and pace off, even if Pro-era values are
    // still stored — those come back automatically if they resubscribe
    private BudgetAlertSettingsDto toSettingsDto(User user) {
        boolean pro = user.isPro();
        return new BudgetAlertSettingsDto(
            pro,
            effectiveThresholds(user),
            pro && user.isPaceAlertsEnabled(),
            ALLOWED_THRESHOLDS);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> DvicheckException.notFound("User"));
    }

    /**
     * Free users always get 80/100. Pro users get their saved CSV, filtered to the allowed
     * set, sorted and de-duplicated; anything unusable falls back to 80/100.
     */
    public List<Integer> effectiveThresholds(User user) {
        String csv = user.getAlertThresholds();
        if (!user.isPro() || csv == null || csv.isBlank()) {
            return DEFAULT_THRESHOLDS;
        }
        List<Integer> parsed = Arrays.stream(csv.split(","))
            .map(String::trim)
            .map(BudgetAlertService::parseIntOrNull)
            .filter(Objects::nonNull)
            .filter(ALLOWED_THRESHOLDS::contains)
            .distinct()
            .sorted()
            .toList();
        return parsed.isEmpty() ? DEFAULT_THRESHOLDS : parsed;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
