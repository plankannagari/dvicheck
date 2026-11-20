package com.dvicheck.backend.service.admin;

import com.dvicheck.backend.dto.AdminOverviewDto;
import com.dvicheck.backend.dto.AdminOverviewDto.*;
import com.dvicheck.backend.model.Referral;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.BudgetAlertLogRepository;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.repository.WeeklyCoachMessageRepository;
import com.dvicheck.backend.service.coach.CoachMessageGenerator;
import com.dvicheck.backend.service.coach.WeeklyCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin overview numbers (Day 38). Aggregate queries only — a fixed ~14 queries however many
 * users exist, never a per-user loop — and nothing personal leaves this class.
 *
 * Time windows are UTC: "new7d"/"activeScanners7d" are rolling (now - 7 days); per-day
 * lists and "this month" use UTC calendar days. Scan activity counts bills.created_at (when
 * the scan happened), not purchase_date (from the receipt, user-editable).
 *
 * Cached 60 s under a constant key (CacheConfig "adminOverview") so a refreshing admin
 * can't hammer the database. The admin check happens in the controller BEFORE this call,
 * so a non-admin never reaches the cache.
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    public static final String CACHE = "adminOverview";
    static final int TREND_DAYS = 14;
    static final int COACH_WEEKS = 4;

    private final UserRepository userRepository;
    private final BillRepository billRepository;
    private final ReferralRepository referralRepository;
    private final BudgetAlertLogRepository budgetAlertLogRepository;
    private final WeeklyCoachMessageRepository coachRepository;

    @Cacheable(value = CACHE, key = "'overview'")
    @Transactional(readOnly = true)
    public AdminOverviewDto overview() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return new AdminOverviewDto(
            users(now),
            activity(now, today),
            pro(now),
            referrals(),
            alerts(now),
            coach(),
            now
        );
    }

    private Users users(Instant now) {
        Object[] row = userRepository.userAggregates(now.minus(7, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS)).get(0);
        long total = num(row[0]);
        long onboarded = num(row[3]);
        double onboardedPercent = total == 0 ? 0.0
            : BigDecimal.valueOf(onboarded * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new Users(total, num(row[1]), num(row[2]), onboardedPercent, num(row[4]), num(row[5]));
    }

    private Activity activity(Instant now, LocalDate today) {
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1);
        // UTC wall-clock bound for the TIMESTAMP-WITHOUT-TIME-ZONE created_at columns
        LocalDateTime trendSince = firstDay.atStartOfDay();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Activity(
            billRepository.countDistinctScannersSince(now.minus(7, ChronoUnit.DAYS)),
            billRepository.countDistinctScannersSince(now.minus(30, ChronoUnit.DAYS)),
            billRepository.countScansSince(monthStart),
            zeroFilledDays(billRepository.countScansPerDay(trendSince), firstDay),
            zeroFilledDays(userRepository.countSignupsPerDay(trendSince), firstDay)
        );
    }

    private Pro pro(Instant now) {
        long paid = userRepository.countPaid(now);
        long bonusOnly = userRepository.countBonusOnly(now);
        return new Pro(paid + bonusOnly, paid, bonusOnly);
    }

    private Referrals referrals() {
        Map<String, Long> byStatus = new HashMap<>();
        for (Object[] row : referralRepository.countGroupedByStatus()) {
            byStatus.put((String) row[0], num(row[1]));
        }
        return new Referrals(byStatus.getOrDefault(Referral.STATUS_PENDING, 0L),
            byStatus.getOrDefault(Referral.STATUS_QUALIFIED, 0L));
    }

    private Alerts alerts(Instant now) {
        List<AlertCount> counts = new ArrayList<>();
        for (Object[] row : budgetAlertLogRepository.countSentSinceGrouped(now.minus(7, ChronoUnit.DAYS))) {
            counts.add(new AlertCount((String) row[0], ((Number) row[1]).intValue(), num(row[2])));
        }
        return new Alerts(counts);
    }

    private Coach coach() {
        // Same week rule as the scheduler: most recent completed Mon-Sun week (UTC)
        LocalDate latestCompleted = WeeklyCoachService.defaultWeekStart();
        LocalDate fromWeek = latestCompleted.minusWeeks(COACH_WEEKS - 1);

        Map<LocalDate, long[]> byWeek = new HashMap<>(); // [ai, fallback]
        for (Object[] row : coachRepository.countReadyBySourceSince(fromWeek)) {
            long[] counts = byWeek.computeIfAbsent((LocalDate) row[0], w -> new long[2]);
            if (CoachMessageGenerator.SOURCE_AI.equals(row[1])) counts[0] += num(row[2]);
            else if (CoachMessageGenerator.SOURCE_FALLBACK.equals(row[1])) counts[1] += num(row[2]);
        }
        List<CoachWeek> weeks = new ArrayList<>();
        for (int i = 0; i < COACH_WEEKS; i++) {
            LocalDate week = fromWeek.plusWeeks(i);
            long[] counts = byWeek.getOrDefault(week, new long[2]);
            weeks.add(new CoachWeek(week, counts[0], counts[1]));
        }
        return new Coach(weeks, coachRepository.findLatestReadyWeekStart());
    }

    // Native [date, count] rows -> one entry per day from firstDay to firstDay+13, zeros included
    static List<DayCount> zeroFilledDays(List<Object[]> rows, LocalDate firstDay) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rows) {
            byDay.put(toLocalDate(row[0]), num(row[1]));
        }
        List<DayCount> days = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = firstDay.plusDays(i);
            days.add(new DayCount(day, byDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    // Native queries may return java.sql.Date or LocalDate depending on the driver/Hibernate
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof Date d) return d.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
