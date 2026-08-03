package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.HomeSummaryResponse;
import com.dvicheck.backend.dto.RecentBillDto;
import com.dvicheck.backend.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final BillService billService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> getSummary() {
        HomeSummaryResponse summary = billService.getHomeSummary(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/recent-bills")
    public ResponseEntity<ApiResponse<List<RecentBillDto>>> getRecentBills(
            @RequestParam(defaultValue = "5") int limit) {
        List<RecentBillDto> bills = billService.getRecentBills(currentUserId(), limit);
        return ResponseEntity.ok(ApiResponse.ok(bills));
    }
}
