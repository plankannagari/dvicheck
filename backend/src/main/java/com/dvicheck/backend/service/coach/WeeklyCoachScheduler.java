package com.dvicheck.backend.service.coach;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Thin trigger; all logic (incl. the app.coach.enabled check) lives in WeeklyCoachService. */
@Component
@RequiredArgsConstructor
public class WeeklyCoachScheduler {

    private final WeeklyCoachService weeklyCoachService;

    @Scheduled(cron = "${app.coach.cron}", zone = "${app.coach.zone}")
    public void run() {
        weeklyCoachService.runWeekly(LocalDate.now(ZoneOffset.UTC));
    }
}
