package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.dto.SpendingTrendsDto;
import com.dvicheck.backend.service.MonthlyReportService;
import com.dvicheck.backend.service.SpendingTrendsService;
import com.dvicheck.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;
    private final SpendingTrendsService spendingTrendsService;
    private final UserService userService;

    private static final int FREE_TREND_WEEKS = 2;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyReportDto>> getMonthlyReport() {
        return ResponseEntity.ok(ApiResponse.ok(monthlyReportService.getMonthlyReport(currentUserId())));
    }

    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<SpendingTrendsDto>> getTrends() {
        UUID userId = currentUserId();
        // getTrends() stays untouched and keeps caching the full 8 weeks for everyone —
        // free users get a trimmed copy here, so the cache never holds a plan-specific shape
        SpendingTrendsDto trends = spendingTrendsService.getTrends(userId);
        if (!userService.findById(userId).isPro()) {
            List<SpendingTrendsDto.WeeklyDataPoint> weeks = trends.weeks();
            // List.copyOf, not a subList view, so the response never aliases the cached list
            List<SpendingTrendsDto.WeeklyDataPoint> lastTwoWeeks =
                List.copyOf(weeks.subList(Math.max(weeks.size() - FREE_TREND_WEEKS, 0), weeks.size()));
            trends = new SpendingTrendsDto(lastTwoWeeks, trends.maxWeekTotal(), trends.avgWeeklySpend());
        }
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }
}
