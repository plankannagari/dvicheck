package com.dvicheck.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProDaysTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private static Instant days(int n) {
        return NOW.plus(n, ChronoUnit.DAYS);
    }

    @Test
    void fromNull_startsNow() {
        assertEquals(days(30), ProDays.extend(null, 30, NOW, 365));
    }

    @Test
    void fromPast_startsNow() {
        assertEquals(days(14), ProDays.extend(days(-5), 14, NOW, 365));
    }

    @Test
    void fromFuture_stacksOnTop() {
        assertEquals(days(50), ProDays.extend(days(20), 30, NOW, 365));
    }

    @Test
    void capApplies() {
        assertEquals(days(180), ProDays.extend(days(170), 30, NOW, 180));
    }

    @Test
    void neverShortensTimeAlreadyBeyondCap() {
        assertEquals(days(300), ProDays.extend(days(300), 30, NOW, 180));
    }

    @Test
    void expiryExactlyNow_treatedAsExpired() {
        assertEquals(days(10), ProDays.extend(NOW, 10, NOW, 365));
    }
}
