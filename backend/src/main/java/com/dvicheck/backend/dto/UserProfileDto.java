package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String phone,
        Integer householdSize,
        String currency,
        Boolean notificationsEnabled,
        Instant createdAt,
        BigDecimal budgetAmount
) {}
