package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.SpendingTrendsDto;
import com.dvicheck.backend.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingTrendsService {

    private static final int WEEK_COUNT = 8;
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM d");

    private final BillRepository billRepository;

    @Transactional(readOnly = true)
    public SpendingTrendsDto getTrends(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);

        List<SpendingTrendsDto.WeeklyDataPoint> weeks = new ArrayList<>();
        for (int i = 0; i < WEEK_COUNT; i++) {
            LocalDate weekStart = currentWeekStart.minusWeeks(WEEK_COUNT - 1 - i);
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(today)) {
                weekEnd = today;
            }

            BigDecimal total = billRepository.sumTotalBetween(userId, weekStart, weekEnd);
            String label = weekStart.format(LABEL_FORMAT);

            weeks.add(new SpendingTrendsDto.WeeklyDataPoint(weekStart, weekEnd, total, label));
        }

        BigDecimal maxWeekTotal = weeks.stream()
            .map(SpendingTrendsDto.WeeklyDataPoint::total)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        if (maxWeekTotal.compareTo(BigDecimal.ZERO) == 0) {
            maxWeekTotal = BigDecimal.ONE;
        }

        BigDecimal sum = weeks.stream()
            .map(SpendingTrendsDto.WeeklyDataPoint::total)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgWeeklySpend = sum.divide(BigDecimal.valueOf(WEEK_COUNT), 2, RoundingMode.HALF_UP);

        return new SpendingTrendsDto(weeks, maxWeekTotal, avgWeeklySpend);
    }
}
