package com.dvicheck.backend.service.referral;

import com.dvicheck.backend.config.ReferralProperties;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.ReferralRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ReferralService.onScanSaved: the after-scan hook (Day 37). Must never throw, and
// pushes only after a reward, only to users with notifications on and a token.
class ReferralServiceHookTest {

    private static final ReferralProperties PROPS = new ReferralProperties(true, 14, 30, 6, 180, 7);
    private static final Map<String, Object> DATA = Map.of("type", "REFERRAL_REWARD");

    private UserRepository userRepository;
    private ReferralRewardTx rewardTx;
    private NotificationService notificationService;
    private ReferralService service;

    private final UUID referrerId = UUID.randomUUID();
    private final UUID refereeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        rewardTx = mock(ReferralRewardTx.class);
        notificationService = mock(NotificationService.class);
        service = new ReferralService(userRepository, mock(ReferralRepository.class),
            mock(ReferralCodeGenerator.class), PROPS, rewardTx, notificationService);
    }

    private void givenUser(UUID id, boolean notificationsEnabled, String token) {
        when(userRepository.findById(id)).thenReturn(Optional.of(User.builder()
            .id(id).phone("+1555" + id.toString().substring(0, 7))
            .notificationsEnabled(notificationsEnabled).pushToken(token).build()));
    }

    private void givenReward(boolean referrerRewarded) {
        when(rewardTx.qualify(refereeId)).thenReturn(Optional.of(
            new ReferralRewardTx.RewardOutcome(referrerId, refereeId, referrerRewarded, 30, 14)));
    }

    // ---- 14 ----

    @Test
    void rewardTxThrows_onScanSavedDoesNotThrow_andNoPush() {
        when(rewardTx.qualify(refereeId)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.onScanSaved(refereeId));
        verify(notificationService, never()).sendNotification(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void pushThrows_onScanSavedDoesNotThrow_andOtherPushStillSent() {
        givenReward(true);
        givenUser(refereeId, true, "ExponentPushToken[referee]");
        givenUser(referrerId, true, "ExponentPushToken[referrer]");
        doThrow(new RuntimeException("expo down")).when(notificationService)
            .sendNotification(eq("ExponentPushToken[referee]"), anyString(), anyString(), anyMap());

        assertDoesNotThrow(() -> service.onScanSaved(refereeId));
        verify(notificationService).sendNotification("ExponentPushToken[referrer]",
            "A friend joined dvicheck", "You earned 30 days of Pro", DATA);
    }

    // ---- 15 ----

    @Test
    void noOutcome_noPushes() {
        when(rewardTx.qualify(refereeId)).thenReturn(Optional.empty());

        service.onScanSaved(refereeId);

        verify(notificationService, never()).sendNotification(anyString(), anyString(), anyString(), anyMap());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void reward_bothEligible_bothPushedWithRewardPayload() {
        givenReward(true);
        givenUser(refereeId, true, "ExponentPushToken[referee]");
        givenUser(referrerId, true, "ExponentPushToken[referrer]");

        service.onScanSaved(refereeId);

        verify(notificationService).sendNotification("ExponentPushToken[referee]",
            "You unlocked 14 days of Pro", "Thanks for joining via an invite", DATA);
        verify(notificationService).sendNotification("ExponentPushToken[referrer]",
            "A friend joined dvicheck", "You earned 30 days of Pro", DATA);
    }

    @Test
    void reward_notificationsOff_noPushForThatUser() {
        givenReward(true);
        givenUser(refereeId, false, "ExponentPushToken[referee]");
        givenUser(referrerId, true, "ExponentPushToken[referrer]");

        service.onScanSaved(refereeId);

        verify(notificationService, never()).sendNotification(eq("ExponentPushToken[referee]"), anyString(), anyString(), anyMap());
        verify(notificationService).sendNotification(eq("ExponentPushToken[referrer]"), anyString(), anyString(), anyMap());
    }

    @Test
    void reward_noToken_noPushForThatUser() {
        givenReward(true);
        givenUser(refereeId, true, null);
        givenUser(referrerId, true, "  ");

        service.onScanSaved(refereeId);

        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void referrerNotRewarded_onlyRefereePushed() {
        givenReward(false);
        givenUser(refereeId, true, "ExponentPushToken[referee]");
        givenUser(referrerId, true, "ExponentPushToken[referrer]");

        service.onScanSaved(refereeId);

        verify(notificationService).sendNotification(eq("ExponentPushToken[referee]"), anyString(), anyString(), anyMap());
        verify(notificationService, never()).sendNotification(eq("ExponentPushToken[referrer]"), anyString(), anyString(), anyMap());
    }

    @Test
    void disabled_noRewardCheckAtAll() {
        ReferralService paused = new ReferralService(userRepository, mock(ReferralRepository.class),
            mock(ReferralCodeGenerator.class), new ReferralProperties(false, 14, 30, 6, 180, 7),
            rewardTx, notificationService);

        paused.onScanSaved(refereeId);

        verify(rewardTx, never()).qualify(any());
    }
}
