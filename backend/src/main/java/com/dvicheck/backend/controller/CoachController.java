package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.CoachMessageDto;
import com.dvicheck.backend.model.WeeklyCoachMessage;
import com.dvicheck.backend.service.coach.CoachFactsBuilder;
import com.dvicheck.backend.service.coach.WeeklyCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/coach")
@RequiredArgsConstructor
public class CoachController {

    private final WeeklyCoachService weeklyCoachService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    // 402 for free users, 404 when no READY message exists yet (both from the service)
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<CoachMessageDto>> getLatest() {
        return ResponseEntity.ok(ApiResponse.ok(toResponse(weeklyCoachService.getLatestReady(currentUserId()))));
    }

    private CoachMessageDto toResponse(WeeklyCoachMessage msg) {
        return new CoachMessageDto(
            CoachFactsBuilder.weekLabel(msg.getWeekStart()),
            msg.getWeekStart(),
            msg.getHeadline(),
            msg.getWin(),
            msg.getWatch(),
            msg.getAction());
    }
}
