package com.dvicheck.backend.service.admin;

import com.dvicheck.backend.config.AdminProperties;
import com.dvicheck.backend.dto.AdminUserDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Referral;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.service.ProDays;
import com.dvicheck.backend.service.coach.WeeklyCoachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin actions on ONE user (Day 38). Every method is audited. Grant and revoke run in one
 * transaction with their audit row; lookup and regenerate are not transactional (lookup is
 * reads + one insert; regenerate makes a multi-second AI call).
 *
 * Audit details never contain phone numbers or amounts — ids, day counts, dates and the
 * admin's reason only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    static final int MIN_REASON = 3;
    static final int MAX_REASON = 200;
    // A run of 7+ digits in a reason is almost certainly a phone number — reject it
    private static final Pattern PHONE_LIKE = Pattern.compile("\\d{7,}");

    private final UserRepository userRepository;
    private final BillRepository billRepository;
    private final ReferralRepository referralRepository;
    private final WeeklyCoachService weeklyCoachService;
    private final AdminAuditService auditService;
    private final AdminProperties properties;

    public AdminUserDto lookup(UUID adminId, String rawPhone) {
        String phone = normalizePhone(rawPhone);
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            // Failed lookups are audited too (enumeration attempts show up in the log)
            auditService.record(adminId, AdminAuditService.USER_LOOKUP, null, "lookup (no match)");
            // Constructor, not notFound(): that factory appends " not found" to its argument
            throw new DvicheckException("NOT_FOUND", "No user with that number");
        }
        auditService.record(adminId, AdminAuditService.USER_LOOKUP, user.getId(), "lookup");
        return toDto(user, Instant.now());
    }

    @Transactional
    public AdminUserDto grantPro(UUID adminId, UUID userId, Integer days, String rawReason) {
        if (days == null || days < 1 || days > properties.maxGrantDays()) {
            throw DvicheckException.badRequest("days must be between 1 and " + properties.maxGrantDays());
        }
        String reason = validReason(rawReason);
        // Row lock: serialises with a referral reward extending the same column
        User user = lockUser(userId);
        Instant now = Instant.now();
        Instant newUntil = ProDays.extend(user.getReferralProUntil(), days, now, properties.maxBonusAheadDays());
        user.setReferralProUntil(newUntil);
        userRepository.save(user);
        auditService.record(adminId, AdminAuditService.GRANT_PRO, userId,
            "days=" + days + " reason=" + reason + " until=" + newUntil);
        log.info("Admin {} granted {} bonus Pro days to user {} (until {})", adminId, days, userId, newUntil);
        return toDto(user, now);
    }

    @Transactional
    public AdminUserDto revokeBonusPro(UUID adminId, UUID userId, String rawReason) {
        String reason = validReason(rawReason);
        User user = lockUser(userId);
        // One column holds referral rewards AND admin grants, so this clears both. The old
        // value goes into the audit row so it can be restored by hand.
        Instant previous = user.getReferralProUntil();
        user.setReferralProUntil(null); // subscription_expires_at untouched (RevenueCat owns it)
        userRepository.save(user);
        auditService.record(adminId, AdminAuditService.REVOKE_BONUS_PRO, userId,
            "reason=" + reason + " previous=" + (previous == null ? "none" : previous.toString()));
        log.info("Admin {} revoked bonus Pro for user {} (was {})", adminId, userId, previous);
        return toDto(user, Instant.now());
    }

    // NOT @Transactional: the AI call takes seconds and must not hold a transaction
    public String regenerateCoach(UUID adminId, UUID userId) {
        loadUser(userId); // 404 for an unknown id before anything is deleted
        LocalDate week = WeeklyCoachService.defaultWeekStart(); // most recent completed week (Day 35)
        WeeklyCoachService.Outcome outcome = weeklyCoachService.regenerate(userId, week);
        String result = outcome == WeeklyCoachService.Outcome.SKIPPED ? "skipped" : outcome.name();
        auditService.record(adminId, AdminAuditService.REGENERATE_COACH, userId,
            "week=" + week + " result=" + result);
        return result;
    }

    /**
     * Stored phones are exactly what the app sent at login: E.164 ("+15084149098").
     * Accepts spaces, dashes, dots and parentheses; 10 bare digits are taken as US (+1).
     * A '+' that arrived as a space (unencoded query param) still works for +1 numbers.
     */
    static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw DvicheckException.badRequest("Enter a phone number");
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        String phone;
        if (trimmed.startsWith("+")) {
            phone = "+" + digits;
        } else if (digits.length() == 10) {
            phone = "+1" + digits;
        } else if (digits.length() == 11 && digits.startsWith("1")) {
            phone = "+" + digits;
        } else {
            throw DvicheckException.badRequest("Enter the number in international format, e.g. +15551234567");
        }
        if (!phone.matches("^\\+[1-9]\\d{7,19}$")) { // same pattern as login (Day 2)
            throw DvicheckException.badRequest("Enter the number in international format, e.g. +15551234567");
        }
        return phone;
    }

    private static String validReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < MIN_REASON || reason.length() > MAX_REASON) {
            throw DvicheckException.badRequest("reason must be " + MIN_REASON + "-" + MAX_REASON + " characters");
        }
        if (PHONE_LIKE.matcher(reason).find()) {
            throw DvicheckException.badRequest("Don't put phone numbers in the reason");
        }
        return reason;
    }

    private AdminUserDto toDto(User user, Instant now) {
        UUID id = user.getId();
        String source = switch (user.proSource(now)) {
            case "PAID" -> "PAID";
            case "REFERRAL" -> "BONUS"; // referral rewards and admin grants share the column
            default -> "NONE";
        };
        String phone = user.getPhone();
        String last4 = phone == null || phone.length() < 4 ? "" : phone.substring(phone.length() - 4);
        boolean pushEnabled = Boolean.TRUE.equals(user.getNotificationsEnabled())
            && user.getPushToken() != null && !user.getPushToken().isBlank();
        return new AdminUserDto(
            id,
            last4,
            user.getCreatedAt(),
            Boolean.TRUE.equals(user.getOnboardingCompleted()),
            user.isProAt(now),
            source,
            user.getSubscriptionExpiresAt(),
            user.getReferralProUntil(),
            billRepository.countByUserId(id),
            billRepository.findLastScanAt(id),
            pushEnabled,
            referralRepository.findByRefereeId(id).map(Referral::getStatus).orElse("NONE"),
            referralRepository.countByReferrerIdAndStatus(id, Referral.STATUS_QUALIFIED)
        );
    }

    private User lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> DvicheckException.notFound("User"));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> DvicheckException.notFound("User"));
    }
}
