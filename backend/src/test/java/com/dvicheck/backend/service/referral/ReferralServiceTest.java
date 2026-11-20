package com.dvicheck.backend.service.referral;

import com.dvicheck.backend.config.ReferralProperties;
import com.dvicheck.backend.dto.RedeemResultDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Referral;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ReferralService: codes + redeem rules (Day 37). JUnit 5 + Mockito, no Spring.
// The service reads Instant.now(); account age is controlled through createdAt.
class ReferralServiceTest {

    private static final ReferralProperties ENABLED = new ReferralProperties(true, 14, 30, 6, 180, 7);
    private static final ReferralProperties DISABLED = new ReferralProperties(false, 14, 30, 6, 180, 7);
    private static final String OWNER_CODE = "ABCD234";

    private UserRepository userRepository;
    private ReferralRepository referralRepository;
    private ReferralCodeGenerator codeGenerator;
    private ReferralService service;

    private final UUID meId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private User me;
    private User owner;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        referralRepository = mock(ReferralRepository.class);
        codeGenerator = mock(ReferralCodeGenerator.class);
        service = newService(ENABLED);

        me = user(meId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        owner = user(ownerId, Instant.now().minus(90, ChronoUnit.DAYS), OWNER_CODE);
        when(userRepository.findById(meId)).thenReturn(Optional.of(me));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findByReferralCode(OWNER_CODE)).thenReturn(Optional.of(owner));
    }

    private ReferralService newService(ReferralProperties props) {
        return new ReferralService(userRepository, referralRepository, codeGenerator, props,
            mock(ReferralRewardTx.class), mock(NotificationService.class));
    }

    private static User user(UUID id, Instant createdAt, String code) {
        return User.builder().id(id).phone("+1555000" + id.toString().substring(0, 4))
            .createdAt(createdAt).referralCode(code).build();
    }

    // ---- 1, 2: codes ----

    @Test
    void getOrCreateCode_existingCode_returnedWithoutSaving() {
        me.setReferralCode("EXIST23");

        assertEquals("EXIST23", service.getOrCreateCode(meId));
        verify(codeGenerator, never()).generate();
        verify(userRepository, never()).setReferralCodeIfAbsent(any(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getOrCreateCode_collision_retriesWithNewCodeAndSucceeds() {
        when(codeGenerator.generate()).thenReturn("TAKEN23", "FRESH45");
        when(userRepository.setReferralCodeIfAbsent(meId, "TAKEN23"))
            .thenThrow(new DataIntegrityViolationException("users_referral_code_key"));
        when(userRepository.setReferralCodeIfAbsent(meId, "FRESH45")).thenReturn(1);

        assertEquals("FRESH45", service.getOrCreateCode(meId));
        verify(codeGenerator, times(2)).generate();
    }

    @Test
    void getOrCreateCode_alwaysColliding_givesUpAfterFiveAttempts() {
        when(codeGenerator.generate()).thenReturn("TAKEN23");
        when(userRepository.setReferralCodeIfAbsent(eq(meId), anyString()))
            .thenThrow(new DataIntegrityViolationException("users_referral_code_key"));

        assertThrows(IllegalStateException.class, () -> service.getOrCreateCode(meId));
        verify(userRepository, times(ReferralService.MAX_CODE_ATTEMPTS)).setReferralCodeIfAbsent(eq(meId), anyString());
    }

    // ---- 3: unknown code ----

    @Test
    void redeem_unknownCode_notFound() {
        when(userRepository.findByReferralCode("ZZZZZZZ")).thenReturn(Optional.empty());

        DvicheckException e = assertThrows(DvicheckException.class, () -> service.redeem(meId, "ZZZZZZZ"));
        assertEquals("NOT_FOUND", e.getErrorCode());
        assertEquals("That invite code isn't valid", e.getMessage());
        verifyNothingSaved();
    }

    // ---- 4: rule rejections ----

    @Test
    void redeem_ownCode_badRequest() {
        me.setReferralCode("MINE234");
        when(userRepository.findByReferralCode("MINE234")).thenReturn(Optional.of(me));

        assertBadRequest(() -> service.redeem(meId, "MINE234"), "You can't use your own invite code");
        verifyNothingSaved();
    }

    @Test
    void redeem_alreadyRedeemed_badRequest() {
        when(referralRepository.existsByRefereeId(meId)).thenReturn(true);

        assertBadRequest(() -> service.redeem(meId, OWNER_CODE), "You've already used an invite code");
        verifyNothingSaved();
    }

    @Test
    void redeem_accountOlderThanWindow_badRequest() {
        me.setCreatedAt(Instant.now().minus(8, ChronoUnit.DAYS));

        assertBadRequest(() -> service.redeem(meId, OWNER_CODE),
            "Invite codes can only be used within 7 days of signing up");
        verifyNothingSaved();
    }

    @Test
    void redeem_reciprocal_ownerAlreadyReferredByMe_badRequest() {
        when(referralRepository.existsByReferrerIdAndRefereeId(meId, ownerId)).thenReturn(true);

        assertBadRequest(() -> service.redeem(meId, OWNER_CODE), "That invite isn't available");
        verifyNothingSaved();
    }

    // ---- 5, 6: success ----

    @Test
    void redeem_codeWithSpacesHyphensLowercase_isNormalisedAndAccepted() {
        RedeemResultDto result = service.redeem(meId, "  abc-d 234 ");

        assertEquals(Referral.STATUS_PENDING, result.status());
        verify(userRepository).findByReferralCode(OWNER_CODE);
        Referral saved = captureSavedReferral();
        assertEquals(OWNER_CODE, saved.getCode());
    }

    @Test
    void redeem_valid_savesPendingAndReturnsMessage() {
        RedeemResultDto result = service.redeem(meId, OWNER_CODE);

        Referral saved = captureSavedReferral();
        assertEquals(ownerId, saved.getReferrerId());
        assertEquals(meId, saved.getRefereeId());
        assertEquals(Referral.STATUS_PENDING, saved.getStatus());

        assertEquals("PENDING", result.status());
        assertEquals(14, result.refereeDays());
        assertEquals("Invite applied. Scan your first receipt to unlock 14 days of Pro.", result.message());
    }

    // ---- 7: race on UNIQUE(referee_id) ----

    @Test
    void redeem_uniqueViolationOnSave_friendlyBadRequest() {
        when(referralRepository.saveAndFlush(any(Referral.class)))
            .thenThrow(new DataIntegrityViolationException("referral_referee_id_key"));

        assertBadRequest(() -> service.redeem(meId, OWNER_CODE), "You've already used an invite code");
    }

    // ---- 8: kill switch ----

    @Test
    void redeem_disabled_pausedBeforeAnyLookup() {
        ReferralService paused = newService(DISABLED);

        assertBadRequest(() -> paused.redeem(meId, OWNER_CODE), "Invites are paused right now");
        verify(userRepository, never()).findByReferralCode(anyString());
        verifyNothingSaved();
    }

    // ---- helpers ----

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable call, String message) {
        DvicheckException e = assertThrows(DvicheckException.class, call);
        assertEquals("BAD_REQUEST", e.getErrorCode());
        assertEquals(message, e.getMessage());
    }

    private void verifyNothingSaved() {
        verify(referralRepository, never()).saveAndFlush(any());
        verify(referralRepository, never()).save(any());
    }

    private Referral captureSavedReferral() {
        ArgumentCaptor<Referral> captor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

}
