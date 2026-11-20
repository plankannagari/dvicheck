package com.dvicheck.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.admin.* (Day 38) — same binding style as ReferralProperties (Day 37's record +
 * @EnableConfigurationProperties; other app.* values still use @Value). Registered in
 * AdminConfig. Missing keys bind to 0, i.e. no grant is ever allowed — a safe default.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        int maxGrantDays,       // most bonus Pro days a single grant action may add
        int maxBonusAheadDays   // referral_pro_until never more than this many days from now via a grant
) {}
