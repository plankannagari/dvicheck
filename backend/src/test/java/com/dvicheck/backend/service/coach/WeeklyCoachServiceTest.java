package com.dvicheck.backend.service.coach;

import com.dvicheck.backend.model.User;
import com.dvicheck.backend.model.WeeklyCoachMessage;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.repository.WeeklyCoachMessageRepository;
import com.dvicheck.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyCoachServiceTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 11, 2);
    private static final String TOKEN = "ExponentPushToken[test]";

    @Mock WeeklyCoachMessageRepository coachRepository;
    @Mock UserRepository userRepository;
    @Mock CoachFactsBuilder factsBuilder;
    @Mock CoachMessageGenerator generator;
    @Mock NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();

    private WeeklyCoachService service(boolean enabled) {
        return new WeeklyCoachService(coachRepository, userRepository, factsBuilder, generator,
            notificationService, enabled);
    }

    // ---- fixtures ----

    private static CoachFacts facts() {
        return new CoachFacts("Nov 2 - Nov 8", "USD 8.99", null, null, 1,
            null, null, null, null, null, "USD");
    }

    private static CoachDraft draft(String source) {
        return new CoachDraft("You spent USD 8.99.", "win", "watch", "action", source, "{\"t\":1}");
    }

    private static User user(boolean notificationsEnabled, String token) {
        return User.builder().notificationsEnabled(notificationsEnabled).pushToken(token).build();
    }

    /** Happy-path stubs up to (and including) the claim + final save. */
    private void readyToGenerate(UUID id, CoachDraft draft) {
        when(coachRepository.existsByUserIdAndWeekStart(id, WEEK)).thenReturn(false);
        when(factsBuilder.build(id, WEEK)).thenReturn(Optional.of(facts()));
        lenient().when(userRepository.getReferenceById(id)).thenReturn(new User());
        lenient().when(coachRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(coachRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(generator.generate(any())).thenReturn(draft);
    }

    // ---- 8: week maths ----

    @Test
    void completedWeekStart_isPreviousMonday() {
        assertThat(WeeklyCoachService.completedWeekStart(LocalDate.of(2026, 11, 9))).isEqualTo(WEEK);  // Monday
        assertThat(WeeklyCoachService.completedWeekStart(LocalDate.of(2026, 11, 11))).isEqualTo(WEEK); // Wednesday
        assertThat(WeeklyCoachService.completedWeekStart(LocalDate.of(2026, 11, 15))).isEqualTo(WEEK); // Sunday
        // Across a year boundary: Mon 2027-01-04 -> Mon 2026-12-28
        assertThat(WeeklyCoachService.completedWeekStart(LocalDate.of(2027, 1, 4))).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    // ---- 9-11: skip paths ----

    @Test
    void alreadyExists_generatorNeverCalled() {
        when(coachRepository.existsByUserIdAndWeekStart(userId, WEEK)).thenReturn(true);

        assertThat(service(true).generate(userId, WEEK)).isEqualTo(WeeklyCoachService.Outcome.SKIPPED);

        verifyNoInteractions(factsBuilder, generator, notificationService);
        verify(coachRepository, never()).saveAndFlush(any());
    }

    @Test
    void noFacts_noRowClaimed() {
        when(coachRepository.existsByUserIdAndWeekStart(userId, WEEK)).thenReturn(false);
        when(factsBuilder.build(userId, WEEK)).thenReturn(Optional.empty());

        assertThat(service(true).generate(userId, WEEK)).isEqualTo(WeeklyCoachService.Outcome.SKIPPED);

        verify(coachRepository, never()).saveAndFlush(any());
        verifyNoInteractions(generator, notificationService);
    }

    @Test
    void claimLostToAnotherRun_generatorNeverCalled_noPush() {
        when(coachRepository.existsByUserIdAndWeekStart(userId, WEEK)).thenReturn(false);
        when(factsBuilder.build(userId, WEEK)).thenReturn(Optional.of(facts()));
        when(userRepository.getReferenceById(userId)).thenReturn(new User());
        when(coachRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThat(service(true).generate(userId, WEEK)).isEqualTo(WeeklyCoachService.Outcome.SKIPPED);

        verifyNoInteractions(generator, notificationService);
        verify(coachRepository, never()).save(any());
    }

    // ---- 12-14: generation + push ----

    @Test
    void success_savesReady_andPushesWeeklyCoach() {
        readyToGenerate(userId, draft(CoachMessageGenerator.SOURCE_AI));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true, TOKEN)));

        assertThat(service(true).generate(userId, WEEK)).isEqualTo(WeeklyCoachService.Outcome.AI);

        // The claim row is inserted GENERATING with status/source set explicitly (DB defaults never apply)
        ArgumentCaptor<WeeklyCoachMessage> claimed = ArgumentCaptor.forClass(WeeklyCoachMessage.class);
        verify(coachRepository).saveAndFlush(claimed.capture());
        assertThat(claimed.getValue().getWeekStart()).isEqualTo(WEEK);
        assertThat(claimed.getValue().getSource()).isNotNull();

        ArgumentCaptor<WeeklyCoachMessage> saved = ArgumentCaptor.forClass(WeeklyCoachMessage.class);
        verify(coachRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        WeeklyCoachMessage row = saved.getValue();
        assertThat(row.getStatus()).isEqualTo(WeeklyCoachService.STATUS_READY);
        assertThat(row.getSource()).isEqualTo(CoachMessageGenerator.SOURCE_AI);
        assertThat(row.getHeadline()).isEqualTo("You spent USD 8.99.");
        assertThat(row.getFactsJson()).isEqualTo("{\"t\":1}");
        assertThat(row.getPushSentAt()).isNotNull();

        verify(notificationService).sendNotification(
            TOKEN, "Your weekly coach", "You spent USD 8.99.", Map.of("type", "WEEKLY_COACH"));
    }

    @Test
    void notificationsDisabled_savedButNoPush() {
        readyToGenerate(userId, draft(CoachMessageGenerator.SOURCE_AI));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(false, TOKEN)));

        service(true).generate(userId, WEEK);

        verify(coachRepository).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void blankToken_savedButNoPush() {
        readyToGenerate(userId, draft(CoachMessageGenerator.SOURCE_AI));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true, "   ")));

        service(true).generate(userId, WEEK);

        ArgumentCaptor<WeeklyCoachMessage> saved = ArgumentCaptor.forClass(WeeklyCoachMessage.class);
        verify(coachRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(WeeklyCoachService.STATUS_READY);
        assertThat(saved.getValue().getPushSentAt()).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    void pushThrows_messageStaysSaved() {
        readyToGenerate(userId, draft(CoachMessageGenerator.SOURCE_AI));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true, TOKEN)));
        org.mockito.Mockito.doThrow(new RuntimeException("expo down"))
            .when(notificationService).sendNotification(anyString(), anyString(), anyString(), anyMap());

        assertThat(service(true).generate(userId, WEEK)).isEqualTo(WeeklyCoachService.Outcome.AI);

        ArgumentCaptor<WeeklyCoachMessage> saved = ArgumentCaptor.forClass(WeeklyCoachMessage.class);
        verify(coachRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(WeeklyCoachService.STATUS_READY);
    }

    @Test
    void fallbackDraft_stillSavedAndPushed() {
        readyToGenerate(userId, draft(CoachMessageGenerator.SOURCE_FALLBACK));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true, TOKEN)));

        assertThat(service(true).generate(userId, WEEK)).isEqualTo(WeeklyCoachService.Outcome.FALLBACK);

        ArgumentCaptor<WeeklyCoachMessage> saved = ArgumentCaptor.forClass(WeeklyCoachMessage.class);
        verify(coachRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
        assertThat(saved.getValue().getStatus()).isEqualTo(WeeklyCoachService.STATUS_READY);
        verify(notificationService).sendNotification(eq(TOKEN), anyString(), anyString(), eq(Map.of("type", "WEEKLY_COACH")));
    }

    // ---- 15-16: runWeekly ----

    @Test
    void runWeekly_oneUserFailing_doesNotStopTheNext() {
        UUID broken = UUID.randomUUID();
        when(userRepository.findProUserIds(any(Instant.class))).thenReturn(List.of(broken, userId));
        when(coachRepository.existsByUserIdAndWeekStart(broken, WEEK)).thenReturn(false);
        when(factsBuilder.build(broken, WEEK)).thenThrow(new RuntimeException("db hiccup"));
        readyToGenerate(userId, draft(CoachMessageGenerator.SOURCE_AI));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true, TOKEN)));

        // Monday 2026-11-09 -> coaches the week of 2026-11-02
        service(true).runWeekly(LocalDate.of(2026, 11, 9));

        verify(generator).generate(any());
        verify(notificationService).sendNotification(eq(TOKEN), anyString(), anyString(), anyMap());
    }

    @Test
    void runWeekly_disabled_doesNothing() {
        service(false).runWeekly(LocalDate.of(2026, 11, 9));

        verifyNoInteractions(coachRepository, userRepository, factsBuilder, generator, notificationService);
    }
}
