package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SpendingTrendsDto(
        List<WeeklyDataPoint> weeks,
        BigDecimal maxWeekTotal,
        BigDecimal avgWeeklySpend
) {
    public record WeeklyDataPoint(
            LocalDate weekStart,
            LocalDate weekEnd,
            BigDecimal total,
            String label
    ) {}
}
