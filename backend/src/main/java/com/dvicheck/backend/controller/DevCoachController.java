package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.service.coach.WeeklyCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * DEV ONLY - disabled automatically when app.auth.dev-mode is false (same switch as Day 33's
 * DevBudgetAlertController). The bean isn't created at all unless dev-mode is "true".
 *
 * Runs the weekly coach on demand for the current user instead of waiting for Monday's
 * schedule. Still requires the normal JWT (falls under anyRequest().authenticated()).
 * No Pro check: it's a dev tool — GET /api/coach/latest still enforces Pro.
 */
@RestController
@RequestMapping("/api/dev/coach")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.dev-mode", havingValue = "true")
public class DevCoachController {

    private final WeeklyCoachService weeklyCoachService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Void>> run(
            @RequestParam(required = false) String weekStart,
            @RequestParam(defaultValue = "false") boolean force) {
        LocalDate week = parseWeekStart(weekStart);
        WeeklyCoachService.Outcome outcome = force
            ? weeklyCoachService.regenerate(currentUserId(), week)
            : weeklyCoachService.generateForUser(currentUserId(), week);

        String result = outcome == WeeklyCoachService.Outcome.SKIPPED
            ? "skipped (no spending or already generated)"
            : "generated " + outcome.name();
        return ResponseEntity.ok(ApiResponse.ok("Coach run for week " + week + ": " + result, null));
    }

    // Taken as a String and parsed here: a LocalDate param that fails to bind would surface as
    // MethodArgumentTypeMismatchException → the catch-all 500, not a clean 400
    private static LocalDate parseWeekStart(String weekStart) {
        if (weekStart == null || weekStart.isBlank()) {
            return WeeklyCoachService.defaultWeekStart();
        }
        LocalDate date;
        try {
            date = LocalDate.parse(weekStart.trim());
        } catch (DateTimeParseException e) {
            throw DvicheckException.badRequest("weekStart must be in yyyy-MM-dd format");
        }
        // Weeks are Monday-Sunday everywhere else; a mid-week start would create a row
        // that never lines up with the scheduler's weeks
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw DvicheckException.badRequest("weekStart must be a Monday");
        }
        return date;
    }
}
