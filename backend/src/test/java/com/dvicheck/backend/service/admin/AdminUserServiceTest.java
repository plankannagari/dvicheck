package com.dvicheck.backend.service.admin;

import com.dvicheck.backend.config.AdminProperties;
import com.dvicheck.backend.dto.AdminUserDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.service.coach.WeeklyCoachService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// AdminUserService (Day 38). JUnit 5 + Mockito, no Spring (so @Transactional/locks aren't
// active — this checks the rules, validation and audit calls).
class AdminUserServiceTest {

    private static final AdminProperties PROPS = new AdminProperties(90, 365);
    private static final Duration TOLERANCE = Duration.ofSeconds(5);
    private static final String PHONE = "+15550000031";
    private static final String TOKEN = "ExponentPushToken[secret-token]";

    private UserRepository userRepository;
    private BillRepository billRepository;
    private ReferralRepository referralRepository;
    private WeeklyCoachService weeklyCoachService;
    private AdminAuditService auditService;
    private AdminUserService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User user;
    private Instant paidUntil;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        billRepository = mock(BillRepository.class);
        referralRepository = mock(ReferralRepository.class);
        weeklyCoachService = mock(WeeklyCoachService.class);
        auditService = mock(AdminAuditService.class);
        service = new AdminUserService(userRepository, billRepository, referralRepository,
            weeklyCoachService, auditService, PROPS);

        paidUntil = Instant.now().minus(3, ChronoUnit.DAYS); // an expired paid subscription
        user = User.builder().id(userId).phone(PHONE).pushToken(TOKEN)
            .notificationsEnabled(true).onboardingCompleted(true)
            .createdAt(Instant.now().minus(40, ChronoUnit.DAYS))
            .subscriptionExpiresAt(paidUntil).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(referralRepository.findByRefereeId(userId)).thenReturn(Optional.empty());
        when(billRepository.countByUserId(userId)).thenReturn(4L);
    }

    private static void assertAbout(Instant expected, Instant actual) {
        assertTrue(Duration.between(expected, actual).abs().compareTo(TOLERANCE) <= 0,
            "expected ~" + expected + " but was " + actual);
    }

    private static void assertBadRequest(Executable call) {
        assertEquals("BAD_REQUEST", assertThrows(DvicheckException.class, call).getErrorCode());
    }

    private void verifyNothingSavedOrAudited() {
        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(any(), anyString(), any(), any());
    }

    // ---- 1: grant validation ----

    @Test
    void grantPro_invalidInput_badRequest_nothingSaved_noAudit() {
        assertBadRequest(() -> service.grantPro(adminId, userId, 0, "support"));
        assertBadRequest(() -> service.grantPro(adminId, userId, 91, "support"));
        assertBadRequest(() -> service.grantPro(adminId, userId, null, "support"));
        assertBadRequest(() -> service.grantPro(adminId, userId, 30, null));
        assertBadRequest(() -> service.grantPro(adminId, userId, 30, "ab"));
        assertBadRequest(() -> service.grantPro(adminId, userId, 30, "x".repeat(201)));
        assertBadRequest(() -> service.grantPro(adminId, userId, 30, "called 5551234567"));

        verifyNothingSavedOrAudited();
        verify(userRepository, never()).findByIdForUpdate(any()); // rejected before locking
        assertNull(user.getReferralProUntil());
    }

    // ---- 2: valid grant ----

    @Test
    void grantPro_noCurrentTime_extendsFromNow_paidUntouched_audited() {
        Instant now = Instant.now();

        service.grantPro(adminId, userId, 30, "  support ticket  ");

        assertAbout(now.plus(30, ChronoUnit.DAYS), user.getReferralProUntil());
        assertEquals(paidUntil, user.getSubscriptionExpiresAt());
        verify(userRepository).save(user);
        verify(auditService).record(eq(adminId), eq(AdminAuditService.GRANT_PRO), eq(userId),
            startsWith("days=30 reason=support ticket until="));
    }

    @Test
    void grantPro_futureCurrentTime_extendsFromThatDate() {
        Instant existing = Instant.now().plus(20, ChronoUnit.DAYS);
        user.setReferralProUntil(existing);

        service.grantPro(adminId, userId, 30, "support");

        assertEquals(existing.plus(30, ChronoUnit.DAYS), user.getReferralProUntil());
        assertEquals(paidUntil, user.getSubscriptionExpiresAt());
    }

    // ---- 3: cap ----

    @Test
    void grantPro_cappedAtNowPlusMaxBonusAheadDays() {
        Instant now = Instant.now();
        user.setReferralProUntil(now.plus(350, ChronoUnit.DAYS)); // +90 would be 440

        service.grantPro(adminId, userId, 90, "support");

        assertAbout(now.plus(365, ChronoUnit.DAYS), user.getReferralProUntil());
    }

    // ---- 4: revoke ----

    @Test
    void revokeBonusPro_clearsBonusOnly_paidUntouched_auditedWithPreviousValue() {
        Instant existing = Instant.now().plus(40, ChronoUnit.DAYS);
        user.setReferralProUntil(existing);

        AdminUserDto result = service.revokeBonusPro(adminId, userId, "abuse report");

        assertNull(user.getReferralProUntil());
        assertNull(result.bonusUntil());
        assertEquals(paidUntil, user.getSubscriptionExpiresAt());
        verify(userRepository).save(user);
        verify(auditService).record(adminId, AdminAuditService.REVOKE_BONUS_PRO, userId,
            "reason=abuse report previous=" + existing);
    }

    @Test
    void revokeBonusPro_missingReason_badRequest_nothingSaved() {
        assertBadRequest(() -> service.revokeBonusPro(adminId, userId, " "));
        verifyNothingSavedOrAudited();
    }

    // ---- 5: lookup ----

    @Test
    void lookup_unknownPhone_notFound_andFailedLookupAudited() {
        when(userRepository.findByPhone("+15559999999")).thenReturn(Optional.empty());

        DvicheckException e = assertThrows(DvicheckException.class, () -> service.lookup(adminId, "+15559999999"));

        assertEquals("NOT_FOUND", e.getErrorCode());
        assertEquals("No user with that number", e.getMessage());
        verify(auditService).record(adminId, AdminAuditService.USER_LOOKUP, null, "lookup (no match)");
    }

    @Test
    void lookup_returnsLast4Only_neverFullPhoneOrToken() throws Exception {
        AdminUserDto dto = service.lookup(adminId, "(555) 000-0031"); // normalised to +15550000031

        assertEquals("0031", dto.phoneLast4());
        assertEquals(userId, dto.id());
        assertEquals(4L, dto.billCount());
        assertTrue(dto.pushEnabled());
        // Structural check: no field holds (or could hold) the full phone or the token
        for (RecordComponent c : AdminUserDto.class.getRecordComponents()) {
            String name = c.getName().toLowerCase();
            assertFalse(name.contains("token"), "DTO exposes a token field: " + c.getName());
            assertTrue(!name.contains("phone") || name.equals("phonelast4"), "DTO exposes a phone field: " + c.getName());
            Object value = c.getAccessor().invoke(dto);
            if (value != null) {
                assertFalse(value.toString().contains(PHONE), c.getName() + " contains the full phone");
                assertFalse(value.toString().contains("5550000031"), c.getName() + " contains the phone digits");
                assertFalse(value.toString().contains(TOKEN), c.getName() + " contains the push token");
            }
        }
        verify(auditService).record(adminId, AdminAuditService.USER_LOOKUP, userId, "lookup");
    }

    @Test
    void normalizePhone_acceptsCommonFormats() {
        assertEquals(PHONE, AdminUserService.normalizePhone("+1 555 000 0031"));
        assertEquals(PHONE, AdminUserService.normalizePhone("5550000031"));
        assertEquals(PHONE, AdminUserService.normalizePhone(" 15550000031")); // '+' lost as a space
        assertBadRequest(() -> AdminUserService.normalizePhone("12345"));
        assertBadRequest(() -> AdminUserService.normalizePhone(null));
    }

    // ---- 6: regenerate ----

    @Test
    void regenerateCoach_callsRegenerateForLastCompletedWeek_andAudits() {
        when(weeklyCoachService.regenerate(eq(userId), any()))
            .thenReturn(WeeklyCoachService.Outcome.FALLBACK);

        String result = service.regenerateCoach(adminId, userId);

        assertEquals("FALLBACK", result);
        verify(weeklyCoachService).regenerate(userId, WeeklyCoachService.defaultWeekStart());
        verify(auditService).record(adminId, AdminAuditService.REGENERATE_COACH, userId,
            "week=" + WeeklyCoachService.defaultWeekStart() + " result=FALLBACK");
    }

    @Test
    void regenerateCoach_skipped_reportedAsSkipped() {
        when(weeklyCoachService.regenerate(eq(userId), any())).thenReturn(WeeklyCoachService.Outcome.SKIPPED);

        assertEquals("skipped", service.regenerateCoach(adminId, userId));
    }

    @Test
    void regenerateCoach_unknownUser_notFound_noRegenerate() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(DvicheckException.class, () -> service.regenerateCoach(adminId, unknown));
        verify(weeklyCoachService, never()).regenerate(any(), any());
        verify(auditService, never()).record(any(), anyString(), any(), any());
    }
}
