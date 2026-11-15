package com.dvicheck.backend.service.coach;

import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.model.WeeklyCoachMessage;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.repository.WeeklyCoachMessageRepository;
import com.dvicheck.backend.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates one coach message per Pro user per completed week.
 *
 * Deliberately NO @Transactional anywhere: each repository call commits on its own, so the
 * claim row is visible to other instances immediately, the multi-second AI call never holds
 * a DB transaction open, and a push failure can't roll back an already-saved message.
 * The UNIQUE(user_id, week_start) constraint is the idempotency lock — two instances (or a
 * manual run overlapping the schedule) can't both generate for the same user/week.
 */
@Service
@Slf4j
public class WeeklyCoachService {

    static final String STATUS_GENERATING = "GENERATING";
    static final String STATUS_READY = "READY";
    static final String PUSH_TITLE = "Your weekly coach";
    // Mobile NotificationListener routes on data.type
    static final Map<String, Object> PUSH_DATA = Map.of("type", "WEEKLY_COACH");

    public enum Outcome { AI, FALLBACK, SKIPPED }

    private final WeeklyCoachMessageRepository coachRepository;
    private final UserRepository userRepository;
    private final CoachFactsBuilder factsBuilder;
    private final CoachMessageGenerator generator;
    private final NotificationService notificationService;
    private final boolean enabled;

    public WeeklyCoachService(WeeklyCoachMessageRepository coachRepository,
                              UserRepository userRepository,
                              CoachFactsBuilder factsBuilder,
                              CoachMessageGenerator generator,
                              NotificationService notificationService,
                              @Value("${app.coach.enabled:true}") boolean enabled) {
        this.coachRepository = coachRepository;
        this.userRepository = userRepository;
        this.factsBuilder = factsBuilder;
        this.generator = generator;
        this.notificationService = notificationService;
        this.enabled = enabled;
    }

    /** Package-private seam (like BudgetAlertService.checkAt): tests pass a fixed date. */
    void runWeekly(LocalDate today) {
        if (!enabled) {
            log.info("Weekly coach: disabled (app.coach.enabled=false), skipping run");
            return;
        }
        LocalDate weekStart = completedWeekStart(today);
        List<UUID> proUserIds = userRepository.findProUserIds(Instant.now());

        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        int failed = 0;
        for (UUID userId : proUserIds) {
            try {
                counts.merge(generate(userId, weekStart), 1, Integer::sum);
            } catch (Exception e) {
                // One user's failure never stops the run
                failed++;
                log.warn("Weekly coach: generation failed for user={} week={} ({})",
                    userId, weekStart, e.getClass().getSimpleName(), e);
            }
        }
        log.info("Weekly coach run week={}: users={} ai={} fallback={} skipped={} failed={}",
            weekStart, proUserIds.size(), counts.getOrDefault(Outcome.AI, 0),
            counts.getOrDefault(Outcome.FALLBACK, 0), counts.getOrDefault(Outcome.SKIPPED, 0), failed);
    }

    /** Monday of the most recent COMPLETED Monday-Sunday week (Monday = the app's week start). */
    static LocalDate completedWeekStart(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    public Outcome generateForUser(UUID userId, LocalDate weekStart) {
        return generate(userId, weekStart);
    }

    /** Dev support: drop any existing row for that user/week, then generate afresh. */
    public Outcome regenerate(UUID userId, LocalDate weekStart) {
        // find + delete(entity) rather than a derived deleteBy, which would need its own transaction
        coachRepository.findByUserIdAndWeekStart(userId, weekStart).ifPresent(coachRepository::delete);
        return generate(userId, weekStart);
    }

    /**
     * Latest READY message for a Pro user. 402 for free users (checked first), 404 when
     * none has been generated yet. Returns the entity; the controller maps the safe fields.
     */
    public WeeklyCoachMessage getLatestReady(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> DvicheckException.notFound("User"));
        if (!user.isPro()) {
            throw DvicheckException.proRequired("The weekly coach is a Pro feature");
        }
        // Constructor, not notFound(): that factory appends " not found" to its argument
        return coachRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, STATUS_READY)
            .orElseThrow(() -> new DvicheckException("NOT_FOUND", "No coach message yet"));
    }

    /** Default week for manual/dev runs — same rule as the scheduler, in UTC. */
    public static LocalDate defaultWeekStart() {
        return completedWeekStart(LocalDate.now(java.time.ZoneOffset.UTC));
    }

    Outcome generate(UUID userId, LocalDate weekStart) {
        if (coachRepository.existsByUserIdAndWeekStart(userId, weekStart)) {
            return Outcome.SKIPPED;
        }
        Optional<CoachFacts> facts = factsBuilder.build(userId, weekStart);
        if (facts.isEmpty()) {
            return Outcome.SKIPPED; // no spending that week — nothing to coach
        }

        Optional<WeeklyCoachMessage> claimed = claim(userId, weekStart);
        if (claimed.isEmpty()) {
            return Outcome.SKIPPED; // another run/instance claimed it first
        }
        WeeklyCoachMessage row = claimed.get();

        // No transaction is open here — the AI call can take seconds
        CoachDraft draft = generator.generate(facts.get());

        row.setHeadline(draft.headline());
        row.setWin(draft.win());
        row.setWatch(draft.watch());
        row.setAction(draft.action());
        row.setSource(draft.source());
        row.setFactsJson(draft.factsJson());
        row.setStatus(STATUS_READY);
        row = coachRepository.save(row);

        pushIfEnabled(userId, row);
        return CoachMessageGenerator.SOURCE_AI.equals(draft.source()) ? Outcome.AI : Outcome.FALLBACK;
    }

    /**
     * Inserts the GENERATING row immediately (saveAndFlush), so UNIQUE(user_id, week_start)
     * rejects a concurrent claimer. status/source are set explicitly: Hibernate writes every
     * column, so the DB defaults never apply and a null would violate NOT NULL.
     */
    private Optional<WeeklyCoachMessage> claim(UUID userId, LocalDate weekStart) {
        WeeklyCoachMessage row = WeeklyCoachMessage.builder()
            .user(userRepository.getReferenceById(userId))
            .weekStart(weekStart)
            .status(STATUS_GENERATING)
            .source(CoachMessageGenerator.SOURCE_AI) // placeholder until the draft is saved
            .build();
        try {
            return Optional.of(coachRepository.saveAndFlush(row));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    /**
     * NotificationService only skips a null/blank token — it does not check
     * notificationsEnabled, so that check lives here. Never throws: a push problem must not
     * undo the saved message.
     */
    private void pushIfEnabled(UUID userId, WeeklyCoachMessage row) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null
                    || !Boolean.TRUE.equals(user.getNotificationsEnabled())
                    || user.getPushToken() == null || user.getPushToken().isBlank()) {
                return;
            }
            notificationService.sendNotification(user.getPushToken(), PUSH_TITLE, row.getHeadline(), PUSH_DATA);
            // NotificationService swallows its own errors and ignores Expo per-ticket errors,
            // so this records "push attempted", not "push delivered"
            row.setPushSentAt(Instant.now());
            coachRepository.save(row);
        } catch (Exception e) {
            log.warn("Weekly coach: push step failed for user={} ({})", userId, e.getClass().getSimpleName());
        }
    }
}
