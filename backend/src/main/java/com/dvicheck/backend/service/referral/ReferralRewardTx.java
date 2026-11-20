package com.dvicheck.backend.service.referral;

import com.dvicheck.backend.config.ReferralProperties;
import com.dvicheck.backend.model.Referral;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * The one transactional step of the referral reward (Day 37): compare-and-set the
 * referral to QUALIFIED, then extend both users' referral_pro_until under row locks.
 * A separate bean so ReferralService.onScanSaved() calls it through the Spring proxy
 * (a self-invoked @Transactional method would run without a transaction).
 * Pushes are NOT sent here — the caller sends them after this commits.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReferralRewardTx {

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final ReferralProperties properties;

    public record RewardOutcome(UUID referrerId, UUID refereeId, boolean referrerRewarded,
                                int referrerDays, int refereeDays) {}

    @Transactional
    public Optional<RewardOutcome> qualify(UUID refereeId) {
        // 1. Only a PENDING referral can qualify (cheap pre-check; the CAS below decides)
        Referral ref = referralRepository.findByRefereeId(refereeId).orElse(null);
        if (ref == null || !Referral.STATUS_PENDING.equals(ref.getStatus())) {
            return Optional.empty();
        }

        // 2. Compare-and-set: exactly one concurrent scan gets 1 here
        Instant now = Instant.now();
        if (referralRepository.markQualified(ref.getId(), now) == 0) {
            return Optional.empty();
        }
        UUID referrerId = ref.getReferrerId();

        // 3. Lock both users in a fixed (ascending id) order so two rewards touching the
        //    same pair can't deadlock. markQualified cleared the persistence context, so
        //    these are fresh reads taken under the lock.
        UUID first = referrerId.compareTo(refereeId) < 0 ? referrerId : refereeId;
        UUID second = first.equals(referrerId) ? refereeId : referrerId;
        User lockedFirst = lock(first);
        User lockedSecond = lock(second);
        User referrer = lockedFirst.getId().equals(referrerId) ? lockedFirst : lockedSecond;
        User referee = lockedFirst.getId().equals(refereeId) ? lockedFirst : lockedSecond;

        referee.setReferralProUntil(extend(referee.getReferralProUntil(), properties.refereeDays(), now));

        // Includes the referral just qualified; the referrer's row lock serialises this
        // count against the referrer's other referees qualifying at the same moment
        long qualifiedCount = referralRepository.countByReferrerIdAndStatus(referrerId, Referral.STATUS_QUALIFIED);
        boolean referrerRewarded = qualifiedCount <= properties.maxRewardedReferrals();
        if (referrerRewarded) {
            referrer.setReferralProUntil(extend(referrer.getReferralProUntil(), properties.referrerDays(), now));
        }

        // 4. Both rows are managed and locked; with @DynamicUpdate on User only
        //    referral_pro_until is written
        userRepository.save(referee);
        userRepository.save(referrer);

        log.info("Referral qualified: referee {} (+{}d), referrer {} ({}; {} qualified)",
            refereeId, properties.refereeDays(), referrerId,
            referrerRewarded ? "+" + properties.referrerDays() + "d" : "cap reached, no days",
            qualifiedCount);
        return Optional.of(new RewardOutcome(referrerId, refereeId, referrerRewarded,
            properties.referrerDays(), properties.refereeDays()));
    }

    /**
     * New referral_pro_until: start from whichever is later (now or the current value),
     * add the days, cap at now + maxProDaysAhead. Never shortens existing time — if
     * current is already beyond the cap (e.g. the cap was lowered in config), it's kept.
     */
    Instant extend(Instant current, int days, Instant now) {
        Instant base = (current != null && current.isAfter(now)) ? current : now;
        Instant candidate = base.plus(days, ChronoUnit.DAYS);
        Instant cap = now.plus(properties.maxProDaysAhead(), ChronoUnit.DAYS);
        Instant capped = candidate.isAfter(cap) ? cap : candidate;
        return (current != null && current.isAfter(capped)) ? current : capped;
    }

    private User lock(UUID id) {
        return userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalStateException("Referral user " + id + " not found"));
    }
}
