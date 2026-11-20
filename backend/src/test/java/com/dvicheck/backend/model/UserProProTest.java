package com.dvicheck.backend.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The Pro rule (Day 37): paid subscription OR referral time. Fixed `now`, no Spring.
class UserProProTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final Instant FUTURE = NOW.plus(10, ChronoUnit.DAYS);
    private static final Instant PAST = NOW.minus(10, ChronoUnit.DAYS);

    private static User user(Instant paidUntil, Instant referralUntil) {
        return User.builder()
            .phone("+15550000000")
            .subscriptionExpiresAt(paidUntil)
            .referralProUntil(referralUntil)
            .build();
    }

    @Test
    void paidActive_isPro() {
        User u = user(FUTURE, null);
        assertTrue(u.isProAt(NOW));
        assertEquals("PAID", u.proSource(NOW));
    }

    @Test
    void paidExpired_referralActive_isPro_sourceReferral() {
        User u = user(PAST, FUTURE);
        assertTrue(u.isProAt(NOW));
        assertEquals("REFERRAL", u.proSource(NOW));
    }

    @Test
    void bothExpired_notPro() {
        User u = user(PAST, PAST);
        assertFalse(u.isProAt(NOW));
        assertEquals("NONE", u.proSource(NOW));
    }

    @Test
    void bothNull_notPro() {
        User u = user(null, null);
        assertFalse(u.isProAt(NOW));
        assertEquals("NONE", u.proSource(NOW));
    }

    @Test
    void paidActive_referralActive_sourcePaid() {
        User u = user(FUTURE, FUTURE.plus(30, ChronoUnit.DAYS));
        assertTrue(u.isProAt(NOW));
        assertEquals("PAID", u.proSource(NOW));
    }

    @Test
    void expiryInstantItself_isNotPro() {
        // isAfter is strict — matches the query's ">" so Java and SQL agree at the boundary
        User u = user(NOW, NOW);
        assertFalse(u.isProAt(NOW));
        assertEquals("NONE", u.proSource(NOW));
    }
}
