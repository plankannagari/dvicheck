package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.model.BudgetAlertLog;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BudgetAlertLogRepository;
import com.dvicheck.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    private static final LocalDate MID_OCTOBER = LocalDate.of(2026, 10, 15);
    private static final String PERIOD = "2026-10";
    private static final String TOKEN = "ExponentPushToken[test]";
    private static final Map<String, Object> ALERT_DATA = Map.of("type", "BUDGET_ALERT");

    @Mock MonthlyReportService monthlyReportService;
    @Mock UserRepository userRepository;
    @Mock BudgetAlertLogRepository budgetAlertLogRepository;
    @Mock NotificationService notificationService;

    @InjectMocks BudgetAlertService service;

    private final UUID userId = UUID.randomUUID();

    // ---- fixtures ----

    private User user(boolean pro, String thresholdsCsv, boolean paceEnabled) {
        User user = User.builder()
            .id(userId)
            .pushToken(TOKEN)
            .alertThresholds(thresholdsCsv)
            .paceAlertsEnabled(paceEnabled)
            // isPro() is derived from this — a future expiry means Pro, null means free
            .subscriptionExpiresAt(pro ? Instant.now().plus(30, ChronoUnit.DAYS) : null)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    private void report(String spent, String budget, double percent) {
        when(monthlyReportService.getMonthlyReport(userId)).thenReturn(new MonthlyReportDto(
            LocalDate.of(2026, 10, 1), MID_OCTOBER,
            new BigDecimal(spent), BigDecimal.ZERO,
            budget == null ? null : new BigDecimal(budget),
            percent, 3, BigDecimal.ZERO, 0.0, Map.of(), List.of()));
    }

    private void alreadyLogged(String alertType, int threshold) {
        when(budgetAlertLogRepository.existsByUserIdAndAlertTypeAndThresholdAndPeriodMonth(
            userId, alertType, threshold, PERIOD)).thenReturn(true);
    }

    private List<String> savedLogs() {
        ArgumentCaptor<BudgetAlertLog> captor = ArgumentCaptor.forClass(BudgetAlertLog.class);
        verify(budgetAlertLogRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream()
            .map(l -> l.getAlertType() + "/" + l.getThreshold() + "/" + l.getPeriodMonth())
            .toList();
    }

    private String onlyPushBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(1)).sendNotification(eq(TOKEN), anyString(), body.capture(), eq(ALERT_DATA));
        return body.getValue();
    }

    // ---- 1-3: free tier thresholds ----

    @Test
    void free_at85_sendsOneApproachingPush_andLogs80() {
        user(false, null, false);
        report("85.00", "100.00", 85.0);

        service.checkAt(userId, MID_OCTOBER);

        verify(notificationService, times(1)).sendNotification(
            eq(TOKEN), eq("Approaching your budget"), anyString(), eq(ALERT_DATA));
        assertThat(savedLogs()).containsExactly("THRESHOLD/80/2026-10");
    }

    @Test
    void free_at110_sendsOneExceededPush_andLogsBoth80And100() {
        user(false, null, false);
        report("110.00", "100.00", 110.0);

        service.checkAt(userId, MID_OCTOBER);

        verify(notificationService, times(1)).sendNotification(
            eq(TOKEN), eq("Budget exceeded"), contains("$10.00"), eq(ALERT_DATA));
        verify(notificationService, times(1)).sendNotification(any(), anyString(), anyString(), anyMap());
        assertThat(savedLogs()).containsExactly("THRESHOLD/80/2026-10", "THRESHOLD/100/2026-10");
    }

    @Test
    void free_withStoredCustomThresholds_stillUses80And100Only() {
        // Downgrade safety: Pro-era settings stay stored but must not apply on the free tier
        User user = user(false, "50,70", false);
        report("75.00", "100.00", 75.0);

        service.checkAt(userId, MID_OCTOBER);

        assertThat(service.effectiveThresholds(user)).containsExactly(80, 100);
        verifyNoInteractions(notificationService);
        assertThat(savedLogs()).isEmpty();
    }

    // ---- 4-6: Pro custom thresholds ----

    @Test
    void pro_customThresholds_at78_pushesFor70_andLogs50And70() {
        user(true, "50,70,90,100", false);
        report("78.00", "100.00", 78.0);

        service.checkAt(userId, MID_OCTOBER);

        String body = onlyPushBody();
        assertThat(body).startsWith("You've used 78% of your October budget");
        assertThat(savedLogs()).containsExactly("THRESHOLD/50/2026-10", "THRESHOLD/70/2026-10");
    }

    @Test
    void pro_thresholdsAlreadyLogged_noPushNoSave() {
        user(true, "50,70,90,100", false);
        report("78.00", "100.00", 78.0);
        alreadyLogged("THRESHOLD", 50);
        alreadyLogged("THRESHOLD", 70);

        service.checkAt(userId, MID_OCTOBER);

        verifyNoInteractions(notificationService);
        verify(budgetAlertLogRepository, never()).save(any());
    }

    @Test
    void pro_midMonthBody_hasPerDayGuidance() {
        user(true, null, false);
        report("85.00", "100.00", 85.0);

        service.checkAt(userId, MID_OCTOBER);

        // Oct 15 of 31 -> 16 days left; $15.00 / 16 = $0.94/day
        assertThat(onlyPushBody())
            .contains("/day")
            .endsWith(" - $15.00 left, about $0.94/day for the remaining 16 days");
    }

    @Test
    void free_midMonthBody_hasNoPerDayGuidance() {
        user(false, null, false);
        report("85.00", "100.00", 85.0);

        service.checkAt(userId, MID_OCTOBER);

        assertThat(onlyPushBody())
            .isEqualTo("You've used 85% of your October budget")
            .doesNotContain("/day");
    }

    // ---- 7-9: pace alerts ----

    @Test
    void pace_proEnabled_projectedOverBudget_sendsOnePaceAlert() {
        user(true, null, true);
        report("200.00", "500.00", 40.0);

        service.checkAt(userId, LocalDate.of(2026, 10, 10));

        // 200 / 10 days * 31 days = 620 -> $120 over
        verify(notificationService, times(1)).sendNotification(
            eq(TOKEN), eq("On pace to overspend"),
            eq("At this rate you'll spend about $620.00 by month end ($120.00 over budget)"),
            eq(ALERT_DATA));
        verify(notificationService, times(1)).sendNotification(any(), anyString(), anyString(), anyMap());
        assertThat(savedLogs()).containsExactly("PACE/0/2026-10");
    }

    @Test
    void pace_alreadyLogged_noPush() {
        user(true, null, true);
        report("200.00", "500.00", 40.0);
        alreadyLogged("PACE", 0);

        service.checkAt(userId, LocalDate.of(2026, 10, 10));

        verifyNoInteractions(notificationService);
        verify(budgetAlertLogRepository, never()).save(any());
    }

    @Test
    void pace_skippedBeforeDay7() {
        user(true, null, true);
        report("200.00", "500.00", 40.0);

        service.checkAt(userId, LocalDate.of(2026, 10, 5));

        verifyNoInteractions(notificationService);
    }

    @Test
    void pace_skippedWhenDisabled() {
        user(true, null, false);
        report("200.00", "500.00", 40.0);

        service.checkAt(userId, LocalDate.of(2026, 10, 10));

        verifyNoInteractions(notificationService);
    }

    @Test
    void pace_skippedAtOrOver100Percent() {
        user(true, null, true);
        report("520.00", "500.00", 104.0);

        service.checkAt(userId, LocalDate.of(2026, 10, 10));

        // The threshold "exceeded" alert still fires; only the pace alert is skipped
        verify(notificationService, never()).sendNotification(
            any(), eq("On pace to overspend"), anyString(), anyMap());
        assertThat(savedLogs()).doesNotContain("PACE/0/2026-10");
    }

    @Test
    void pace_skippedForNonPro() {
        user(false, null, true);
        report("200.00", "500.00", 40.0);

        service.checkAt(userId, LocalDate.of(2026, 10, 10));

        verifyNoInteractions(notificationService);
    }

    // ---- 10-12: guards and parsing ----

    @Test
    void noBudget_noNotificationInteraction() {
        report("85.00", null, 0.0);

        service.checkAt(userId, MID_OCTOBER);

        verifyNoInteractions(notificationService);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void notificationFailure_neverThrowsOutward() {
        user(false, null, false);
        report("85.00", "100.00", 85.0);
        doThrow(new RuntimeException("Expo down"))
            .when(notificationService).sendNotification(any(), anyString(), anyString(), anyMap());

        assertDoesNotThrow(() -> service.checkAt(userId, MID_OCTOBER));
    }

    @Test
    void effectiveThresholds_filtersInvalid_andFallsBackToDefaults() {
        User user = User.builder()
            .subscriptionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .build();

        user.setAlertThresholds(" 100, 60, abc, 50, 90, 90, 0, 101,, ");
        assertThat(service.effectiveThresholds(user)).containsExactly(50, 90, 100);

        user.setAlertThresholds("60, abc, 150");
        assertThat(service.effectiveThresholds(user)).containsExactly(80, 100);

        user.setAlertThresholds("   ");
        assertThat(service.effectiveThresholds(user)).containsExactly(80, 100);
    }
}
