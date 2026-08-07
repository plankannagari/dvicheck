package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklyInsightDto(
        LocalDate weekStart,
        LocalDate weekEnd,
        long billsScanned,
        BigDecimal totalSpent,
        BigDecimal avoidableSpend,
        BigDecimal prevWeekTotal,
        double vsLastWeekPercent,
        List<TopItemDto> topItems,
        Map<String, BigDecimal> spendByCategory,
        String pattern
) {
    public record TopItemDto(
            String name,
            BigDecimal totalPrice,
            String category
    ) {}
}
