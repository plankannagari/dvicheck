package com.dvicheck.backend.service.coach;

/** source = CoachMessageGenerator.SOURCE_AI or SOURCE_FALLBACK. */
public record CoachDraft(
        String headline,
        String win,
        String watch,
        String action,
        String source,
        String factsJson
) {}
