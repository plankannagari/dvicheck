package com.dvicheck.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(UUID lineItemId, String feedback, Instant createdAt) {}
