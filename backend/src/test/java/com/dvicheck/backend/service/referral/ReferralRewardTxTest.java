package com.dvicheck.backend.service.referral;

import com.dvicheck.backend.config.ReferralProperties;
import com.dvicheck.backend.model.Referral;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ReferralRewardTx.qualify: compare-and-set + day extension (Day 37). No Spring, so
// @Transactional isn't active here — this tests the logic, not the locking.
class ReferralRewardTxTest {

    // refereeDays 14, referrerDays 30, max 6 rewarded referrals, cap 180 days ahead
    private static final ReferralProperties PROPS = new ReferralProperties(true, 14, 30, 6, 180, 7);
    // qualify() reads Instant.now(); allow this much drift between it and the test's own clock
    private static final Duration TOLERANCE = Duration.ofSeconds(5);

    private ReferralRepository referralRepository;
    private UserRepository userRepository;
    private ReferralRewardTx tx;

    private final UUID referrerId = UUID.randomUUID();
    private final UUID refereeId = UUID.randomUUID();
    private final UUID referralId = UUID.randomUUID();
    private User referrer;
    private User referee;

    @BeforeEach
    void setUp() {
        referralRepository = mock(ReferralRepository.class);
        userRepository = mock(UserRepository.class);
        tx = new ReferralRewardTx(referralRepository, userRepository, PROPS);

        referrer = User.builder().id(referrerId).phone("+15550000001").build();
        referee = User.builder().id(refereeId).phone("+15550000002").build();
        when(userRepository.findByIdForUpdate(referrerId)).thenReturn(Optional.of(referrer));
        when(userRepository.findByIdForUpdate(refereeId)).thenReturn(Optional.of(referee));
    }

    private void pendingReferral() {
        Referral ref = Referral.builder().id(referralId).referrerId(referrerId).refereeId(refereeId)
            .code("ABCD234").status(Referral.STATUS_PENDING).build();
        when(referralRepository.findByRefereeId(refereeId)).thenReturn(Optional.of(ref));
        when(referralRepository.markQualified(eq(referralId), any())).thenReturn(1);
        when(referralRepository.countByReferrerIdAndStatus(referrerId, Referral.STATUS_QUALIFIED)).thenReturn(1L);
    }

    private static void assertAbout(Instant expected, Instant actual) {
        assertTrue(Duration.between(expected, actual).abs().compareTo(TOLERANCE) <= 0,
            "expected ~" + expected + " but was " + actual);
    }

    private void verifyNoUserTouched() {
        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userRepository, never()).save(any());
    }

    // ---- 9: nothing to qualify ----

    @Test
    void noReferral_empty() {
        when(referralRepository.findByRefereeId(refereeId)).thenReturn(Optional.empty());

        assertTrue(tx.qualify(refereeId).isEmpty());
        verify(referralRepository, never()).markQualified(any(), any());
        verifyNoUserTouched();
    }

    @Test
    void alreadyQualified_empty() {
        when(referralRepository.findByRefereeId(refereeId)).thenReturn(Optional.of(
            Referral.builder().id(referralId).referrerId(referrerId).refereeId(refereeId)
                .code("ABCD234").status(Referral.STATUS_QUALIFIED).build()));

        assertTrue(tx.qualify(refereeId).isEmpty());
        verify(referralRepository, never()).markQualified(any(), any());
        verifyNoUserTouched();
    }

    @Test
    void lostCompareAndSet_empty_andNoUserSaved() {
        pendingReferral();
        when(referralRepository.markQualified(eq(referralId), any())).thenReturn(0);

        assertTrue(tx.qualify(refereeId).isEmpty());
        verifyNoUserTouched();
    }

    // ---- 10: plain success ----

    @Test
    void success_bothGetNowPlusDays() {
        pendingReferral();
        Instant now = Instant.now();

        var outcome = tx.qualify(refereeId).orElseThrow();

        assertAbout(now.plus(14, ChronoUnit.DAYS), referee.getReferralProUntil());
        assertAbout(now.plus(30, ChronoUnit.DAYS), referrer.getReferralProUntil());
        assertTrue(outcome.referrerRewarded());
        assertEquals(referrerId, outcome.referrerId());
        assertEquals(refereeId, outcome.refereeId());
        assertEquals(14, outcome.refereeDays());
        assertEquals(30, outcome.referrerDays());
        verify(userRepository).save(referee);
        verify(userRepository).save(referrer);
    }

    // ---- 11: stacking on existing future time ----

    @Test
    void existingFutureTime_extendedFromThatDate() {
        pendingReferral();
        Instant now = Instant.now();
        Instant refereeExisting = now.plus(10, ChronoUnit.DAYS);
        Instant referrerExisting = now.plus(20, ChronoUnit.DAYS);
        referee.setReferralProUntil(refereeExisting);
        referrer.setReferralProUntil(referrerExisting);

        tx.qualify(refereeId);

        assertEquals(refereeExisting.plus(14, ChronoUnit.DAYS), referee.getReferralProUntil());
        assertEquals(referrerExisting.plus(30, ChronoUnit.DAYS), referrer.getReferralProUntil());
    }

    @Test
    void expiredTime_extendedFromNow() {
        pendingReferral();
        Instant now = Instant.now();
        referee.setReferralProUntil(now.minus(5, ChronoUnit.DAYS));

        tx.qualify(refereeId);

        assertAbout(now.plus(14, ChronoUnit.DAYS), referee.getReferralProUntil());
    }

    // ---- 12: cap ----

    @Test
    void extension_cappedAtNowPlusMaxProDaysAhead() {
        pendingReferral();
        Instant now = Instant.now();
        referrer.setReferralProUntil(now.plus(170, ChronoUnit.DAYS)); // +30 would be 200

        tx.qualify(refereeId);

        assertAbout(now.plus(180, ChronoUnit.DAYS), referrer.getReferralProUntil());
    }

    @Test
    void extend_neverShortensTimeAlreadyBeyondCap() {
        Instant now = Instant.now();
        Instant beyondCap = now.plus(200, ChronoUnit.DAYS);

        assertEquals(beyondCap, tx.extend(beyondCap, 30, now));
    }

    // ---- 13: referrer over the reward limit ----

    @Test
    void referrerOverMaxRewarded_refereeRewarded_referrerNot() {
        pendingReferral();
        when(referralRepository.countByReferrerIdAndStatus(referrerId, Referral.STATUS_QUALIFIED))
            .thenReturn(7L); // includes this one; max is 6
        Instant now = Instant.now();

        var outcome = tx.qualify(refereeId).orElseThrow();

        assertFalse(outcome.referrerRewarded());
        assertNull(referrer.getReferralProUntil());
        assertAbout(now.plus(14, ChronoUnit.DAYS), referee.getReferralProUntil());
    }

    @Test
    void referrerExactlyAtMax_stillRewarded() {
        pendingReferral();
        when(referralRepository.countByReferrerIdAndStatus(referrerId, Referral.STATUS_QUALIFIED))
            .thenReturn(6L);

        assertTrue(tx.qualify(refereeId).orElseThrow().referrerRewarded());
    }
}
