package com.dvicheck.backend.dto;

import java.time.Instant;
import java.util.UUID;

// One user's STATUS for the admin lookup (Day 38). Deliberately no receipts, items, amounts,
// store names, tokens, invite code or full phone number — phoneLast4 only.
public record AdminUserDto(
        UUID id,
        String phoneLast4,
        Instant createdAt,
        boolean onboardingCompleted,
        boolean proActive,
        String proSource,         // PAID | BONUS | NONE (PAID wins when both are active)
        Instant paidUntil,        // subscription_expires_at (RevenueCat)
        Instant bonusUntil,       // referral_pro_until (referral rewards + admin grants)
        long billCount,
        Instant lastScanAt,
        boolean pushEnabled,      // push token present AND notifications enabled
        String referralStatus,    // NONE | PENDING | QUALIFIED — this user as a referee
        long qualifiedReferrals   // friends this user referred who have qualified
) {}
