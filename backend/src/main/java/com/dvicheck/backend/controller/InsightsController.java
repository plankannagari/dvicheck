package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.WeeklyInsightDto;
import com.dvicheck.backend.service.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<WeeklyInsightDto>> getWeeklyInsights() {
        return ResponseEntity.ok(ApiResponse.ok(insightsService.getWeeklyInsights(currentUserId())));
    }
}
