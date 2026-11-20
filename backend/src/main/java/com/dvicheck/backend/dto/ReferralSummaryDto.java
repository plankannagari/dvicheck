package com.dvicheck.backend.dto;

import java.time.Instant;

public record ReferralSummaryDto(
        String code,
        int refereeDays,
        int referrerDays,
        int qualifiedCount,
        int pendingCount,
        int maxRewardedReferrals,
        Instant referralProUntil,
        String myReferralStatus, // NONE | PENDING | QUALIFIED
        boolean canRedeem
) {}
