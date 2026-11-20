package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.RedeemReferralRequest;
import com.dvicheck.backend.dto.RedeemResultDto;
import com.dvicheck.backend.dto.ReferralSummaryDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.service.referral.ReferralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Invite codes (Day 37). Open to free and Pro users alike — no proRequired.
@RestController
@RequestMapping("/api/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ReferralSummaryDto>> me() {
        UUID userId = currentUserId();
        // Ensure a code exists so the summary always carries one (summary() also does this)
        referralService.getOrCreateCode(userId);
        return ResponseEntity.ok(ApiResponse.ok(referralService.summary(userId)));
    }

    // required = false + explicit null check: a missing body would otherwise raise
    // HttpMessageNotReadableException, which GlobalExceptionHandler has no case for (-> 500)
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RedeemResultDto>> redeem(
            @Valid @RequestBody(required = false) RedeemReferralRequest request) {
        if (request == null) {
            throw DvicheckException.badRequest("Enter an invite code");
        }
        return ResponseEntity.ok(ApiResponse.ok(referralService.redeem(currentUserId(), request.code())));
    }
}
