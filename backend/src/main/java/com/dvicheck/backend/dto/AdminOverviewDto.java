package com.dvicheck.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// GET /api/admin/overview (Day 38). Counts and trends only — no personal data, no amounts.
public record AdminOverviewDto(
        Users users,
        Activity activity,
        Pro pro,
        Referrals referrals,
        Alerts alerts,
        Coach coach,
        Instant generatedAt
) {
    public record Users(long total, long new7d, long new30d, double onboardingCompletedPercent,
                        long withPushToken, long notificationsEnabled) {}

    // Days are UTC calendar days; both lists are zero-filled, oldest first
    public record Activity(long activeScanners7d, long activeScanners30d, long scansThisMonth,
                           List<DayCount> scansPerDay, List<DayCount> signupsPerDay) {}

    public record DayCount(LocalDate date, long count) {}

    // total = paid + bonusOnly
    public record Pro(long total, long paid, long bonusOnly) {}

    public record Referrals(long pending, long qualified) {}

    public record Alerts(List<AlertCount> sentLast7d) {}

    public record AlertCount(String alertType, int threshold, long count) {}

    // weeks: the last 4 completed weeks, oldest first, zero-filled; READY messages only
    public record Coach(List<CoachWeek> weeks, LocalDate latestWeekStart) {}

    public record CoachWeek(LocalDate weekStart, long aiCount, long fallbackCount) {}
}
