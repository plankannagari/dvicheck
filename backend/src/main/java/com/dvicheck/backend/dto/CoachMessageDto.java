package com.dvicheck.backend.dto;

import java.time.LocalDate;

// Deliberately excludes source, facts_json, ids and push fields
public record CoachMessageDto(
        String weekLabel,
        LocalDate weekStart,
        String headline,
        String win,
        String watch,
        String action
) {}
