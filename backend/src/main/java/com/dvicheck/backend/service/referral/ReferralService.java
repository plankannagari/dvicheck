package com.dvicheck.backend.service.referral;

import com.dvicheck.backend.config.ReferralProperties;
import com.dvicheck.backend.dto.RedeemResultDto;
import com.dvicheck.backend.dto.ReferralSummaryDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Referral;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

// Invite codes, summary and redemption (Day 37). Rewarding on the referee's first
// scan lives separately (compare-and-set + row lock in one transaction).
// No @Transactional here: every write is a single statement.
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralService {

    static final int MAX_CODE_ATTEMPTS = 5;
    static final String STATUS_NONE = "NONE";
    // Push data payload; mobile routes taps on this type (exact Map shape, like BUDGET_ALERT)
    static final Map<String, Object> PUSH_DATA = Map.of("type", "REFERRAL_REWARD");

    private final UserRepository userRepository;
    private final ReferralRepository referralRepository;
    private final ReferralCodeGenerator codeGenerator;
    private final ReferralProperties properties;
    private final ReferralRewardTx rewardTx;
    private final NotificationService notificationService;

    public String getOrCreateCode(UUID userId) {
        User user = loadUser(userId);
        if (user.getReferralCode() != null) {
            return user.getReferralCode();
        }
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            try {
                if (userRepository.setReferralCodeIfAbsent(userId, code) == 1) {
                    return code;
                }
                // 0 rows: a concurrent request set a code first — return that one
                return loadUser(userId).getReferralCode();
            } catch (DataIntegrityViolationException e) {
                // Another user already owns this code (UNIQUE referral_code) — try a new one
                log.warn("Referral code collision for user {} (attempt {}/{})", userId, attempt, MAX_CODE_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Could not generate a unique referral code after "
            + MAX_CODE_ATTEMPTS + " attempts");
    }

    public ReferralSummaryDto summary(UUID userId) {
        String code = getOrCreateCode(userId);
        User user = loadUser(userId); // re-read: getOrCreateCode may have just set the code

        String myStatus = referralRepository.findByRefereeId(userId)
            .map(Referral::getStatus)
            .orElse(STATUS_NONE);
        boolean canRedeem = properties.enabled()
            && STATUS_NONE.equals(myStatus)
            && withinRedeemWindow(user, Instant.now());

        return new ReferralSummaryDto(
            code,
            properties.refereeDays(),
            properties.referrerDays(),
            (int) referralRepository.countByReferrerIdAndStatus(userId, Referral.STATUS_QUALIFIED),
            (int) referralRepository.countByReferrerIdAndStatus(userId, Referral.STATUS_PENDING),
            properties.maxRewardedReferrals(),
            user.getReferralProUntil(),
            myStatus,
            canRedeem
        );
    }

    public RedeemResultDto redeem(UUID userId, String rawCode) {
        // 1
        if (!properties.enabled()) {
            throw DvicheckException.badRequest("Invites are paused right now");
        }
        // 2
        String code = ReferralCodeGenerator.normalize(rawCode);
        if (code.isBlank()) {
            throw DvicheckException.badRequest("Enter an invite code");
        }
        // 3 — constructor, not notFound(): that factory appends " not found" to its argument
        User owner = userRepository.findByReferralCode(code)
            .orElseThrow(() -> new DvicheckException("NOT_FOUND", "That invite code isn't valid"));
        // 4
        if (owner.getId().equals(userId)) {
            throw DvicheckException.badRequest("You can't use your own invite code");
        }
        // 5
        if (referralRepository.existsByRefereeId(userId)) {
            throw DvicheckException.badRequest("You've already used an invite code");
        }
        // 6
        User user = loadUser(userId);
        if (!withinRedeemWindow(user, Instant.now())) {
            throw DvicheckException.badRequest("Invite codes can only be used within "
                + properties.redeemWindowDays() + " days of signing up");
        }
        // 7 — reciprocal: the owner was already invited by this user
        if (referralRepository.existsByReferrerIdAndRefereeId(userId, owner.getId())) {
            throw DvicheckException.badRequest("That invite isn't available");
        }

        Referral referral = Referral.builder()
            .referrerId(owner.getId())
            .refereeId(userId)
            .code(code)
            .status(Referral.STATUS_PENDING)
            .build();
        try {
            referralRepository.saveAndFlush(referral);
        } catch (DataIntegrityViolationException e) {
            // Lost a race on UNIQUE(referee_id) with a concurrent redeem by the same user
            throw DvicheckException.badRequest("You've already used an invite code");
        }
        log.info("Referral redeemed: referee {} -> referrer {}", userId, owner.getId());

        return new RedeemResultDto(
            Referral.STATUS_PENDING,
            properties.refereeDays(),
            "Invite applied. Scan your first receipt to unlock " + properties.refereeDays() + " days of Pro."
        );
    }

    /**
     * Called after every saved scan. Qualifies a PENDING referral (first scan after
     * redeeming) and rewards both users. NOT transactional and never throws outward —
     * same contract as BudgetAlertService.checkAndSendAlerts — so a referral problem can
     * never fail or roll back an already-saved bill.
     */
    public void onScanSaved(UUID userId) {
        try {
            if (!properties.enabled()) return;
            var outcome = rewardTx.qualify(userId); // commits before returning
            if (outcome.isEmpty()) return;
            var o = outcome.get();

            // Pushes only after the reward has committed; each one isolated, so a failed
            // push can never undo or fail the reward (or stop the other push)
            push(o.refereeId(), "You unlocked " + o.refereeDays() + " days of Pro",
                "Thanks for joining via an invite");
            if (o.referrerRewarded()) {
                push(o.referrerId(), "A friend joined dvicheck",
                    "You earned " + o.referrerDays() + " days of Pro");
            }
        } catch (Exception e) {
            log.warn("Referral reward check failed for user {}: {}", userId, e.toString());
        }
    }

    // NotificationService only checks the token, not notificationsEnabled — checked here
    // (same as WeeklyCoachService.pushIfEnabled)
    private void push(UUID userId, String title, String body) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null
                    || !Boolean.TRUE.equals(user.getNotificationsEnabled())
                    || user.getPushToken() == null || user.getPushToken().isBlank()) {
                return;
            }
            notificationService.sendNotification(user.getPushToken(), title, body, PUSH_DATA);
        } catch (Exception e) {
            log.warn("Referral push failed for user {}: {}", userId, e.toString());
        }
    }

    // Account age <= redeemWindowDays, from users.created_at
    boolean withinRedeemWindow(User user, Instant now) {
        Instant createdAt = user.getCreatedAt();
        if (createdAt == null) return false;
        return !now.isAfter(createdAt.plus(properties.redeemWindowDays(), ChronoUnit.DAYS));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> DvicheckException.notFound("User"));
    }
}
