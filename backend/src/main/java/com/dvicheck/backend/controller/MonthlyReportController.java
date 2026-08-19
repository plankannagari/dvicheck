package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.dto.SpendingTrendsDto;
import com.dvicheck.backend.service.MonthlyReportService;
import com.dvicheck.backend.service.SpendingTrendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;
    private final SpendingTrendsService spendingTrendsService;

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
        return ResponseEntity.ok(ApiResponse.ok(spendingTrendsService.getTrends(currentUserId())));
    }
}
