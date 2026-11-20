package com.dvicheck.backend.dto;

import jakarta.validation.constraints.NotNull;

// Only null is rejected here (400 VALIDATION_ERROR); blank or badly formatted codes go to
// ReferralService.redeem, which normalises them and answers "Enter an invite code"
public record RedeemReferralRequest(
        @NotNull(message = "Enter an invite code")
        String code
) {}
