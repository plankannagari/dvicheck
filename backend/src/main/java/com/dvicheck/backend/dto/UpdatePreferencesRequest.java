package com.dvicheck.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdatePreferencesRequest(
        @Min(1) @Max(10) Integer householdSize,
        @Size(min = 3, max = 3) String currency,
        Boolean notificationsEnabled
) {}
