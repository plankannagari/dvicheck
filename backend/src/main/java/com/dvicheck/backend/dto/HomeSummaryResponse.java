package com.dvicheck.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

public record HomeSummaryResponse(
        long billsScanned,
        BigDecimal totalSpent,
        BigDecimal avoidableSpend,
        BigDecimal estimatedSaved,
        long duplicatesCaught,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String topSuggestion
) {}
