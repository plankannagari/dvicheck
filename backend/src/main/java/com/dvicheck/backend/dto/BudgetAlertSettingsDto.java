package com.dvicheck.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BudgetAlertSettingsDto(
        // Pinned to "isPro" to match UserProfileDto — Jackson can otherwise strip the
        // "is" prefix from a primitive boolean and emit "pro"
        @JsonProperty("isPro") boolean isPro,
        List<Integer> thresholds,
        boolean paceAlertsEnabled,
        List<Integer> allowedThresholds
) {}
