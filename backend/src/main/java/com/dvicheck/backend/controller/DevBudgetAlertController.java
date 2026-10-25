package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.service.BudgetAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * DEV ONLY - disabled automatically when app.auth.dev-mode is false.
 *
 * Runs the budget-alert check on demand for the current user, so alerts can be
 * tested without scanning a receipt (normally the check only runs after a scan).
 * The bean isn't even created unless dev-mode is "true", so the route doesn't
 * exist in production. Still requires the normal JWT — no SecurityConfig
 * exception, it falls under anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/dev/budget-alerts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.dev-mode", havingValue = "true")
public class DevBudgetAlertController {

    private final BudgetAlertService budgetAlertService;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<Void>> check() {
        // checkAndSendAlerts never throws and returns nothing — this can't report what
        // fired; check budget_alert_log or the backend log for the outcome
        budgetAlertService.checkAndSendAlerts(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Budget alert check ran", null));
    }

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }
}
