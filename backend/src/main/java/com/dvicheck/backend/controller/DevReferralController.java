package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.service.referral.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * DEV ONLY - disabled automatically when app.auth.dev-mode is false (same switch as Days 33
 * and 35: DevBudgetAlertController, DevCoachController). The bean isn't created at all
 * unless dev-mode is "true".
 *
 * Runs the after-scan referral hook for the current user without scanning a receipt.
 * Still requires the normal JWT (falls under anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/dev/referral")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.dev-mode", havingValue = "true")
public class DevReferralController {

    private final ReferralService referralService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @PostMapping("/qualify")
    public ResponseEntity<ApiResponse<Void>> qualify() {
        UUID userId = currentUserId();
        referralService.onScanSaved(userId); // never throws; no-op unless a PENDING referral exists
        String status = referralService.summary(userId).myReferralStatus();
        return ResponseEntity.ok(ApiResponse.ok("Referral check ran - your referral status: " + status, null));
    }
}
