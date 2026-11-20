package com.dvicheck.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Bonus-Pro day arithmetic for users.referral_pro_until (Day 37 referral rewards, Day 38
 * admin grants). Pure static helper — callers pass their own cap.
 */
public final class ProDays {

    private ProDays() {}

    /**
     * New expiry: start from whichever is later (now or the current value), add the days,
     * cap at now + maxAheadDays. Never shortens existing time — if current is already
     * beyond the cap (e.g. an admin grant up to 365 days, then a referral reward capped at
     * 180, or a cap lowered in config), current is kept.
     */
    public static Instant extend(Instant current, int days, Instant now, int maxAheadDays) {
        Instant base = (current != null && current.isAfter(now)) ? current : now;
        Instant candidate = base.plus(days, ChronoUnit.DAYS);
        Instant cap = now.plus(maxAheadDays, ChronoUnit.DAYS);
        Instant capped = candidate.isAfter(cap) ? cap : candidate;
        return (current != null && current.isAfter(capped)) ? current : capped;
    }
}
