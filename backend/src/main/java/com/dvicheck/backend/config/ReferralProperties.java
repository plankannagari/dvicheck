package com.dvicheck.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.referral.* (Day 37) — the first @ConfigurationProperties class in this codebase;
 * every other app.* value is still bound with @Value. Registered via
 * @EnableConfigurationProperties in ReferralConfig.
 *
 * Missing keys bind to false / 0, i.e. invites paused and no rewards — a safe default.
 */
@ConfigurationProperties(prefix = "app.referral")
public record ReferralProperties(
        boolean enabled,
        int refereeDays,          // Pro days the new user gets when they qualify
        int referrerDays,         // Pro days the inviter gets per qualified referral
        int maxRewardedReferrals, // inviter stops earning after this many qualified referrals
        int maxProDaysAhead,      // referral_pro_until never more than this many days from now
        int redeemWindowDays      // a code can only be redeemed this many days after signup
) {}
